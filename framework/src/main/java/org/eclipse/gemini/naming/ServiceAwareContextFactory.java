/*******************************************************************************
 * Copyright (c) 2010, 2015 Oracle.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution. 
 * The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 * and the Apache License v2.0 is available at 
 *     http://www.opensource.org/licenses/apache2.0.php.
 * You may elect to redistribute this code under either of these licenses.
 *
 * Contributors:
 *     Bob Nettleton (Oracle) - Initial Reference Implementation
 ******************************************************************************/  

package org.eclipse.gemini.naming;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.PrivilegedExceptionAction;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import javax.naming.directory.DirContext;
import javax.naming.ldap.LdapContext;
import javax.naming.spi.InitialContextFactory;

class ServiceAwareContextFactory {
	
	private static Logger logger = Logger.getLogger(ServiceAwareContextFactory.class.getName());
	
	/* private constructor to disallow creation of this class */
	private ServiceAwareContextFactory() {}
	
	static Context createServiceAwareContextWrapper(InitialContextFactory factory, Context internalContext, FactoryManager manager) {
		return (Context) Proxy.newProxyInstance(ServiceAwareContextFactory.class.getClassLoader(),
                								new Class[] {Context.class}, 
                								new DefaultServiceAwareInvocationHandler(factory, internalContext, manager));
	}
	
	static DirContext createServiceAwareDirContextWrapper(InitialContextFactory factory, DirContext internalContext, FactoryManager manager) {
		return (DirContext) Proxy.newProxyInstance(ServiceAwareContextFactory.class.getClassLoader(),
												new Class[] {DirContext.class},
												new DefaultServiceAwareInvocationHandler(factory, internalContext, manager));
	}

	static LdapContext createServiceAwareLdapContextWrapper(InitialContextFactory factory, LdapContext internalContext, FactoryManager manager) {
		return (LdapContext) Proxy.newProxyInstance(ServiceAwareContextFactory.class.getClassLoader(),
												new Class[] {LdapContext.class},
												new DefaultServiceAwareInvocationHandler(factory, internalContext, manager));
	}

	private static class DefaultServiceAwareInvocationHandler implements InvocationHandler {

		private InitialContextFactory m_factory;
		private Context m_context;
		private final FactoryManager m_manager;
		private final Object lock = new Object();
		private volatile boolean m_isOpen;
		
		DefaultServiceAwareInvocationHandler(InitialContextFactory factory, Context context, FactoryManager manager) {
			m_factory = factory;
			m_context = context;
			m_manager = manager;
			m_isOpen = true;
		}
		
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			try {
				return invokeContextMethod(method, args);
			}
			catch (Exception exception) {
				if(exception instanceof NamingException) {
					throw (NamingException)exception;
				}

				if (logger.isLoggable(Level.FINE)) {
					logger.log(Level.FINE,
						"Exception occurred during a doPrivileged call",
						exception);
				}
				// if the cause was not a NamingException, wrap the
				// cause in NamingException and throw back to caller
				NamingException namingException = new NamingException("Exception occured during a Context method invocation");
				namingException.setRootCause(exception);
				throw namingException;
			}
		}

		private Object invokeContextMethod(Method method, Object[] args) throws Throwable {
			if (m_isOpen) {
				synchronized (lock) {
					if (m_isOpen) {
						if (!method.getName().equals("close")) {
							synchronized (m_manager) {
								if (!isFactoryServiceActive()) {
									SecurityUtils.invokePrivilegedActionNoReturn(new ObtainFactory());
								}
							}
						} else {
							// if context is already closed, do not try to
							// rebind the backing service
							// simply forward the call to the underlying context implementation
							m_isOpen = false;
						}
					}
				}
			}
			return ReflectionUtils.invokeMethodOnContext(method, m_context, args);
		}

		private void obtainNewFactory() throws NamingException, Throwable, NoInitialContextException {
			// make copy of existing context's environment
			Hashtable newContextEnvironment = new Hashtable();
			if (m_context.getEnvironment() != null) {
				newContextEnvironment.putAll(m_context
						.getEnvironment());
			}
			// attempt to recreate the required factory and context
			try {
				InitialContextFactory newFactory = m_manager
						.createInitialContextFactory(newContextEnvironment);
				if (newFactory != null) {
					m_factory = newFactory;
					Context newInternalContext = m_factory
							.getInitialContext(newContextEnvironment);
					if (newInternalContext != null) {
						m_context = newInternalContext;
						return;
					}
				}
			}
			catch (NoInitialContextException noContextException) {
				logger.log(Level.SEVERE,
						   "An exception occurred while attempting to rebind the JNDI Provider service for this Context",
						   noContextException);
			}

			// if no InitialContextFactory service can handle this request, throw exception
			throw new NoInitialContextException(
					"The service that created this JNDI Context is not available");
		}
		

		/**
		 * Query to see if the IntialContextFactory used
		 * to create this context is still active
		 * 
		 * @return true if factory service is still active
		 *         false if factory service is no longer active
		 */
		private boolean isFactoryServiceActive() {
			if(m_factory instanceof BuilderSupportedInitialContextFactory) {
				return m_manager.isFactoryServiceActive(((BuilderSupportedInitialContextFactory)m_factory).getBuilder());
			} else {
				return m_manager.isFactoryServiceActive(m_factory);
			}
		}
	
		private class ObtainFactory implements PrivilegedExceptionAction {
			
			@Override
			public Object run() throws Exception {
				try {
					obtainNewFactory();
					return null;
				} catch (Throwable e) {
					if(e instanceof NamingException) {
						throw (NamingException)e;
					}
					
					NamingException namingException = new NamingException("Error while attempting to obtain factory service on behalf of Context");
					namingException.setRootCause(e);
					throw namingException;
				}
			}
			
		}
	
	}
}

