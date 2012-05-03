/*******************************************************************************
 * Copyright (c) 2010, 2012 Oracle.
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

import javax.naming.*;
import javax.naming.spi.ObjectFactory;

import java.security.PrivilegedExceptionAction;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Decorated Context class that will allow the Gemini Naming Framework to handle
 * requests for URL context factory lookups.
 * 
 */
class ContextWrapperImpl implements Context {

	private static Logger logger = Logger.getLogger(ContextWrapperImpl.class.getName());
	
	private final Context			m_context;
	private final FactoryManager	m_factoryManager;
	
	public ContextWrapperImpl(Context context, FactoryManager factoryManager) {
		m_context = context;
		m_factoryManager = factoryManager;
	}


	public Object lookup(Name name) throws NamingException {
		return getURLContextOrDefaultContext(name.toString()).lookup(name);
	}

	public Object lookup(String name) throws NamingException {
		return getURLContextOrDefaultContext(name).lookup(name);
	}

	public void bind(Name name, Object obj) throws NamingException {
		getURLContextOrDefaultContext(name.toString()).bind(name, obj);
	}

	public void bind(String name, Object obj) throws NamingException {
		getURLContextOrDefaultContext(name).bind(name, obj);
	}

	public void rebind(Name name, Object obj) throws NamingException {
		getURLContextOrDefaultContext(name.toString()).rebind(name, obj);
	}

	public void rebind(String name, Object obj) throws NamingException {
		getURLContextOrDefaultContext(name).rebind(name, obj);
	}

	public void unbind(Name name) throws NamingException {
		getURLContextOrDefaultContext(name.toString()).unbind(name);
	}

	public void unbind(String name) throws NamingException {
		getURLContextOrDefaultContext(name).unbind(name);
	}

	public void rename(Name oldName, Name newName) throws NamingException {
		getURLContextOrDefaultContext(oldName.toString()).rename(oldName, newName);
	}

	public void rename(String oldName, String newName) throws NamingException {
		getURLContextOrDefaultContext(oldName).rename(oldName, newName);
	}

	public NamingEnumeration list(Name name) throws NamingException {
		return getURLContextOrDefaultContext(name.toString()).list(name);
	}

	public NamingEnumeration list(String name) throws NamingException {
		return getURLContextOrDefaultContext(name).list(name);
	}

	public NamingEnumeration listBindings(Name name) throws NamingException {
		return getURLContextOrDefaultContext(name.toString()).listBindings(name);
	}

	public NamingEnumeration listBindings(String name) throws NamingException {
		return getURLContextOrDefaultContext(name).listBindings(name);
	}

	public void destroySubcontext(Name name) throws NamingException {
		getURLContextOrDefaultContext(name.toString()).destroySubcontext(name);
	}

	public void destroySubcontext(String name) throws NamingException {
		getURLContextOrDefaultContext(name).destroySubcontext(name);
	}

	public Context createSubcontext(Name name) throws NamingException {
		return getURLContextOrDefaultContext(name.toString()).createSubcontext(name);
	}

	public Context createSubcontext(String name) throws NamingException {
		return getURLContextOrDefaultContext(name).createSubcontext(name);
	}

	public Object lookupLink(Name name) throws NamingException {
		return getURLContextOrDefaultContext(name.toString()).lookupLink(name);
	}

	public Object lookupLink(String name) throws NamingException {
		return getURLContextOrDefaultContext(name).lookupLink(name);
	}

	public NameParser getNameParser(Name name) throws NamingException {
		return getURLContextOrDefaultContext(name.toString()).getNameParser(name);
	}

	public NameParser getNameParser(String name) throws NamingException {
		return getURLContextOrDefaultContext(name).getNameParser(name);
	}

	public Name composeName(Name name, Name prefix) throws NamingException {
		return getURLContextOrDefaultContext(name.toString()).composeName(name, prefix);
	}

	public String composeName(String name, String prefix)
			throws NamingException {
		return getURLContextOrDefaultContext(name).composeName(name, prefix);
	}

	public Object addToEnvironment(String propName, Object propVal)
			throws NamingException {
		return m_context.addToEnvironment(propName, propVal);
	}

	public Object removeFromEnvironment(String propName) throws NamingException {
		return m_context.removeFromEnvironment(propName);
	}

	public Hashtable getEnvironment() throws NamingException {
		return m_context.getEnvironment();
	}

	public void close() throws NamingException {
		m_context.close();
	}

	public String getNameInNamespace() throws NamingException {
		return m_context.getNameInNamespace();
	}

	private static boolean isURLRequest(String name) {
		int indexOfColon = name.indexOf(":");
		return (indexOfColon != -1);
	}

	private static String getScheme(String name) {
		int indexOfColon = name.indexOf(":");
		if (indexOfColon != -1) {
			return name.substring(0, indexOfColon);
		}

		return null;
	}

	private Context getURLContextOrDefaultContext(String name)
			throws NameNotFoundException, NamingException {
		if (isURLRequest(name)) {
			// attempt to find a URL Context Factory to satisfy this request
			ObjectFactory objectFactory = null;
			try {
				// obtain URL Context Factory in a doPrivilieged() block
				objectFactory = (ObjectFactory)SecurityUtils.invokePrivilegedAction(new GetObjectFactoryAction(m_factoryManager, name));
			} catch (Exception e) {
				logger.log(Level.FINE, 
						   "Exception occurred while trying to obtain a reference to a URL Context Factory.",
						   e);
			}
			
			if (objectFactory == null) {
				throw new NameNotFoundException(
						"Name: "
								+ name
								+ " was not found.  A URL Context Factory was not registered to handle "
								+ "this URL scheme");
			}

			try {
				Context context = 
					(Context) objectFactory.getObjectInstance(null, null, 
															  null, m_context.getEnvironment());
				if (context != null) {
					return context;
				}
				else {
					throw new NamingException("Name = " + name
							+ "was not found using the URL Context factory = " + objectFactory);
				}
			}
			catch (Exception e) {
				if (e instanceof NamingException) {
					// re-throw naming exceptions
					throw (NamingException) e;
				}

				NamingException namingException = 
					new NameNotFoundException("Exception occurred during URL Context Factory Resolution for name = "
								              + name);
				namingException.initCause(e);
				throw namingException;
			}
		}
		else {
			// treat this lookup as a normal lookup
			return m_context;
		}
	}
	
	private static class GetObjectFactoryAction implements PrivilegedExceptionAction {
		private final FactoryManager m_factoryManager;
		private final String m_name;
		
		GetObjectFactoryAction(FactoryManager factoryManager, String name) {
			m_factoryManager = factoryManager;
			m_name = name;
		}

		public Object run() throws Exception {
			return obtainObjectFactory(m_name);
		}
		
		private ObjectFactory obtainObjectFactory(String name) {
			ObjectFactory objectFactory;
			synchronized (m_factoryManager) {
				objectFactory = m_factoryManager.getURLContextFactory(getScheme(name));
			}
			return objectFactory;
		}
	}
}
