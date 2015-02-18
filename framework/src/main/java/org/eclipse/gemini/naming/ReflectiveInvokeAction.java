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

import java.lang.reflect.Method;
import java.security.PrivilegedExceptionAction;

/**
 * This class represents a privileged action that involves 
 * invoking a method reflectively.  
 * 
 * This class does not make the reflective call.  It provides
 * common exception-handling mechanisms for sub-classes to rely upon.  
 *
 * @version $Revision$
 */
abstract class ReflectiveInvokeAction implements PrivilegedExceptionAction {

	private final Method m_method;
	private final Object[] m_args;
	
	ReflectiveInvokeAction(Method method, Object[] args) {
		m_method = method;
		m_args = args;
	}
	
	@Override
	public Object run() throws Exception {
		try {
			return invokeMethod(m_method, m_args);
		}
		catch (Exception exception) {
			// re-throw exception
			throw exception;
		}
		catch (Throwable throwable) {
			// the method that was invoked reflectively must have thrown a Throwable
			// in this case, wrap the Throwable in an exception
			throw new Exception("Exception occurred during method invocation", throwable);
		}
	}
	
	public abstract Object invokeMethod(Method method, Object[] args) throws Throwable;
}
