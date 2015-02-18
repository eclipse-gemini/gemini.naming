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
import javax.naming.Name;
import javax.naming.directory.Attributes;


/**
 * Decorator for the CloseableProviderAdmin that can handle invoking methods on the 
 * underlying JNDIProviderAdmin implementation in a doPrivileged() Action.
 *
 * 
 * @version $Revision$
 */
class SecurityAwareProviderAdminImpl implements CloseableProviderAdmin {

	private static final Logger logger = 
		Logger.getLogger(SecurityAwareProviderAdminImpl.class.getName());
	
	private final CloseableProviderAdmin m_closeableProviderAdmin;
	
	public SecurityAwareProviderAdminImpl(CloseableProviderAdmin closeableProviderAdmin) {
		m_closeableProviderAdmin = closeableProviderAdmin;
	}
		
	@Override
	public Object getObjectInstance(Object refInfo, Name name, Context context, Map environment) throws Exception {
		PrivilegedExceptionAction action = 
			new GetObjectInstanceAction(refInfo, name, context, environment);
		return invokePrivilegedAction(action);
	}

	@Override
	public Object getObjectInstance(Object refInfo, Name name, Context context, Map environment, Attributes attributes) throws Exception {
		PrivilegedExceptionAction action = 
			new GetObjectInstanceActionWithAttributes(refInfo, name, context, environment, attributes);
		return invokePrivilegedAction(action);
	}
	
	@Override
	public void close() {
		try {
			SecurityUtils.invokePrivilegedActionNoReturn(new CloseAction());
		}
		catch (Exception exception) {
			logger.log(Level.FINE, 
					   "Exception occurred while trying to close this JNDIProviderAdmin implementation",
					   exception);
		}
	}
	
	private static Object invokePrivilegedAction(final PrivilegedExceptionAction action) throws Exception {
		return SecurityUtils.invokePrivilegedAction(action);
	}
	
	
	private class GetObjectInstanceAction implements PrivilegedExceptionAction {
		protected final Object m_refInfo;
		protected final Name m_name;
		protected final Context m_context;
		protected final Map m_environment;
		
		GetObjectInstanceAction(Object refInfo, Name name, Context context, Map environment) {
			m_refInfo = refInfo;
			m_name = name;
			m_context = context;
			m_environment = environment;
		}
		
		@Override
		public Object run() throws Exception {
			return m_closeableProviderAdmin.getObjectInstance(m_refInfo, 
					                                     m_name, 
					                                     m_context, 
					                                     m_environment);
		}
		
	}
	
	private class GetObjectInstanceActionWithAttributes extends GetObjectInstanceAction {
		private final Attributes m_attributes;
		
		GetObjectInstanceActionWithAttributes(Object refInfo, Name name, Context context, Map environment, Attributes attributes) {
			super(refInfo, name, context, environment);
			m_attributes = attributes;
		}
		
		
		@Override
		public Object run() throws Exception {
			return m_closeableProviderAdmin.getObjectInstance(m_refInfo, 
					                                     m_name, 
					                                     m_context, 
					                                     m_environment, 
					                                     m_attributes);
		}
		
	}
	
	
	private class CloseAction implements PrivilegedExceptionAction {
		@Override
		public Object run() throws Exception {
			m_closeableProviderAdmin.close();
			return null;
		}
		
	}
}
