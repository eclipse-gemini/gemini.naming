/*******************************************************************************
 * Copyright (c) 2013 SAP AG.
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
 *     Violeta Georgieva (SAP AG) - Initial Contribution
 ******************************************************************************/
package org.eclipse.gemini.naming;

import javax.naming.ldap.Control;
import javax.naming.ldap.ExtendedRequest;
import javax.naming.ldap.ExtendedResponse;
import javax.naming.ldap.LdapContext;

import org.easymock.EasyMockSupport;

import junit.framework.TestCase;

import static org.easymock.EasyMock.*;

public class LdapContextWrapperImplTestCase extends TestCase {

	public void testCreate() throws Exception {
		// setup mocks
		EasyMockSupport mockSupport = new EasyMockSupport();
		LdapContext ldapContextMock =
			mockSupport.createMock(LdapContext.class);
		FactoryManager factoryManagerMock =
			mockSupport.createMock(FactoryManager.class);

		mockSupport.replayAll();
		// begin test
		new LdapContextWrapperImpl(ldapContextMock, factoryManagerMock);

		mockSupport.verifyAll();
	}


	/**
	 * This test verifies that all of the methods on the wrapper for the LdapContext interface
	 * will delegate to the internal LdapContext implementation.  The test also verifies that the
	 * parameters passed into the wrapper are passed unchanged to the internal
	 * implementation.
	 *
	 */
	public void testDelegatedMethods() throws Exception {
		// mock setup
		EasyMockSupport mockSupport = new EasyMockSupport();
		LdapContext ldapContextMock =
			mockSupport.createMock(LdapContext.class);
		LdapContext subContextMock =
			mockSupport.createMock(LdapContext.class);
		FactoryManager factoryManagerMock =
			mockSupport.createMock(FactoryManager.class);
		ExtendedResponse extendedResponseMock =
			mockSupport.createMock(ExtendedResponse.class);
		ExtendedRequest extendedRequestMock =
			mockSupport.createMock(ExtendedRequest.class);
		Control[] controls = new Control[0];
		//setup of expected method calls on internal LdapContext
		expect(ldapContextMock.extendedOperation(extendedRequestMock)).andReturn(extendedResponseMock);
		expect(ldapContextMock.newInstance(controls)).andReturn(subContextMock);
		ldapContextMock.reconnect(controls);
		expect(ldapContextMock.getConnectControls()).andReturn(controls);
		ldapContextMock.setRequestControls(controls);
		expect(ldapContextMock.getRequestControls()).andReturn(controls);
		expect(ldapContextMock.getResponseControls()).andReturn(controls);


		mockSupport.replayAll();

		// begin test
		LdapContext testDirContext =
			new LdapContextWrapperImpl(ldapContextMock, factoryManagerMock);

		assertSame("LdapContextWrapperImpl did not return expected ExtendedResponse",
				extendedResponseMock, testDirContext.extendedOperation(extendedRequestMock));
		assertSame("LdapContextWrapperImpl did not return expected subContext",
				subContextMock, testDirContext.newInstance(controls));
		testDirContext.reconnect(controls);
		assertSame("LdapContextWrapperImpl did not return expected controls",
				controls, testDirContext.getConnectControls());
		testDirContext.setRequestControls(controls);
		assertSame("LdapContextWrapperImpl did not return expected controls",
				controls, testDirContext.getRequestControls());
		assertSame("LdapContextWrapperImpl did not return expected controls",
				controls, testDirContext.getResponseControls());

		mockSupport.verifyAll();
	}

}
