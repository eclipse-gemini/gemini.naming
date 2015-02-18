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

import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import javax.naming.directory.DirContext;


/**
 * Decorator for the CloseableContextManager that can handle invoking methods on the 
 * underlying context manager implementation in a doPrivileged() Action. 
 * 
 * @version $Revision$
 */
class SecurityAwareContextManagerImpl implements CloseableContextManager {

	private static final Logger logger = 
		Logger.getLogger(SecurityAwareContextManagerImpl.class.getName());
	
	private final CloseableContextManager m_contextManager;
	
	public SecurityAwareContextManagerImpl(CloseableContextManager contextManager) {
		m_contextManager = contextManager;
	}
	
	
	@Override
	public Context newInitialContext() throws NamingException {
		return (Context)invokePrivilegedAction(new NewInitialContextAction());
	}
	

	@Override
	public Context newInitialContext(Map environment) throws NamingException {
		return (Context)invokePrivilegedAction(new NewInitialContextWithEnvironmentAction(environment));
	}

	
	@Override
	public DirContext newInitialDirContext() throws NamingException {
		return (DirContext)invokePrivilegedAction(new NewInitialDirContextAction());
	}

	
	@Override
	public DirContext newInitialDirContext(Map environment) throws NamingException {
		return (DirContext)invokePrivilegedAction(new NewInitialDirContextWithEnvironmentAction(environment));
	}
	
	@Override
	public void close() {
		invokePrivilegedActionWithoutReturn(new CloseContextManagerAction());
	}
	

	private static Object invokePrivilegedAction(final PrivilegedExceptionAction action) throws NamingException {
		try {
			return SecurityUtils.invokePrivilegedAction(action);
		} catch (Exception exception) {
			if(exception instanceof NamingException) {
				throw (NamingException)exception;
			} else {
				logExceptionFromPrivilegedAction(exception);
				
				NamingException namingException = 
					new NoInitialContextException("Error occurred during a privileged operation");
				namingException.setRootCause(exception);
				throw namingException;
			}
		}
	}
	
	private static void invokePrivilegedActionWithoutReturn(final PrivilegedExceptionAction action) {
		try {
			SecurityUtils.invokePrivilegedActionNoReturn(action);
		}
		catch (Exception exception) {
			logExceptionFromPrivilegedAction(exception);
		}
	}
	
	
	private static void logExceptionFromPrivilegedAction(Exception e) {
		logger.log(Level.FINE, 
				   "Exception occurred while invoking a PrivilegedAction",
				   e);
	}
	
	
	
	// actions for each of the operations supported by the JNDIContextManager service
	private class NewInitialContextAction implements PrivilegedExceptionAction {
		@Override
		public Object run() throws Exception {
			return m_contextManager.newInitialContext();
		}
	}
	
	private class NewInitialContextWithEnvironmentAction implements PrivilegedExceptionAction {

		private final Map m_environment;
		
		public NewInitialContextWithEnvironmentAction(Map environment) {
			m_environment = environment;
		}
		
		@Override
		public Object run() throws Exception {
			return m_contextManager.newInitialContext(m_environment);
		}
	}
	
	
	private class NewInitialDirContextAction implements PrivilegedExceptionAction {
		@Override
		public Object run() throws Exception {
			return m_contextManager.newInitialDirContext();
		}
	}
	
	private class NewInitialDirContextWithEnvironmentAction implements PrivilegedExceptionAction {
		private final Map m_environment;
		
		public NewInitialDirContextWithEnvironmentAction(Map environment) {
			m_environment = environment;
		}
		
		@Override
		public Object run() throws Exception {
			return m_contextManager.newInitialDirContext(m_environment);
		}
	}
	
	private class CloseContextManagerAction implements PrivilegedExceptionAction {
		@Override
		public Object run() throws Exception {
			m_contextManager.close();
			return null;
		}
		
	}
}
