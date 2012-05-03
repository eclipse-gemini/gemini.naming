/*******************************************************************************
 * Copyright (c) 2012 Oracle.
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
 *     Bob Nettleton - Initial Developer tests for Reference Implementation
 ******************************************************************************/
 
package org.eclipse.gemini.naming.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.spi.InitialContextFactory;
import javax.naming.spi.InitialContextFactoryBuilder;
import javax.naming.spi.NamingManager;
import javax.naming.spi.ObjectFactory;
import javax.naming.spi.ObjectFactoryBuilder;

import org.osgi.framework.*;


/**
 * Test case used to verify the Factory resolution process
 * in Gemini Naming.  
 * 
 */
public class FactoryResolutionTestCase extends NamingTestCase {

	private ClassLoader m_oldClassLoader = null;
	
	protected void onSetUp() throws Exception {
		super.onSetUp();
		m_oldClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(new TestClassLoaderTwo());
	}

	
	protected void onTearDown() throws Exception {
		super.onTearDown();
		Thread.currentThread().setContextClassLoader(m_oldClassLoader);
	}
	
	
	/**
	 * Verifies that the Factory Manager allows access to the JNDI implementations
	 * provided by the JDK.  This test will verify access to the RMI JNDI implementation as 
	 * an example of how to test this.  
	 * 
	 * @TODO, add tests for the other factories provided by the JDK
	 * @throws Exception
	 */
	public void testAccessToJDK_RMIContext() throws Exception {
		Hashtable environment = new Hashtable();
		environment.put(Context.INITIAL_CONTEXT_FACTORY, 
				        "com.sun.jndi.rmi.registry.RegistryContextFactory");
		environment.put("osgi.service.jndi.bundleContext", bundleContext);
		
		// verify that context can be created without any errors
		Context context = null;
		try {
			context = new InitialContext(environment);
		} finally {
			context.close();
		}
	}
	
	/**
	 * Verifies access to the LDAP context factory provided by the 
	 * JDK.  This test does not verify any LDAP connectivity, it merely 
	 * ensures that the JDK-provided implementation is available in an OSGi
	 * environment.  
	 * 
	 * @throws Exception
	 */
	public void testAccessToJDK_LDAPContext() throws Exception {
		Hashtable environment = new Hashtable();
		environment.put(Context.INITIAL_CONTEXT_FACTORY, 
				        "com.sun.jndi.ldap.LdapCtxFactory");
		environment.put("osgi.service.jndi.bundleContext", bundleContext);
		
		DirContext dirContext = null;
		try {
			dirContext = new InitialDirContext(environment);
			// verify that a communication exception is thrown, 
			// since this test does not include an LDAP server
			// the intention of this test is to verify that the factory 
			// can be made available by the factory manager.  
			fail("CommunicationException should have been thrown");
		}
		catch (CommunicationException communicationException) {
			// expected exception for this test
			assertTrue("Did not receive expected root exception for this test",
					    communicationException.getCause() instanceof ConnectException); 
		} finally {
			if (dirContext != null) {
				dirContext.close();
			}
		}
	}

	/**
	 * Verify that if an ObjectFactory classname is specified in a Reference, and that
	 * ObjectFactory is not available to the Factory Manager, that the manager will 
	 * query the existing ObjectFactoryBuilder services in order to attempt to 
	 * find a matching ObjectFactory.  
	 * 
	 * Please see section 5.2.2.1 of RFC 142 for more details.
	 * 
	 * @throws Exception
	 */
	public void testSpecificObjectFactoryResolvedByBuilder() throws Exception {
		final int expectedValue = 100;
		// stub builder to be used in test
		ObjectFactoryBuilder factoryBuilder = new ObjectFactoryBuilder() {
			public ObjectFactory createObjectFactory(Object var0, Hashtable var1) throws NamingException {
				return new ObjectFactory() {
				    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable environment) throws Exception {
				        return new Integer(expectedValue);
				    }
				};
			}
        };
        
        // only register the builder implementation
        registerService(ObjectFactoryBuilder.class.getName(), factoryBuilder, null);

        // reference data does not matter, since we're testing that
        // the factory manager can locate the only ObjectFactory registered.
        Reference reference = new Reference("test", "com.test.factory.DoesNotExist", null);
        Object result = NamingManager.getObjectInstance(reference, null, null, null);
        assertEquals("JNDI Factory Manager did not locate the correct ObjectFactory", 
                      new Integer(expectedValue), result);
	}
	
	
	/**
	 * Verify that the Factory Manager can resolve a reference that includes an 
	 * address (StringRefAddr) of type "URL".  This address can be used by the Factory
	 * Manager in order to locate a URL context factory that supports the given scheme.  
	 * 
	 * Please see section 5.2.2.1 of RFC 142 for more details.
	 * 
	 * @throws Exception
	 */
	public void testObjectFactoryResolvedUsingURLContextFactory() throws Exception {
		final int expectedValue = 100;
		ObjectFactory urlContextFactory = new ObjectFactory() {
			public Object getObjectInstance(Object var0, Name var1,
					Context var2, Hashtable var3) throws Exception {
				return new Integer(expectedValue);
			}
			
		};
		
		Hashtable serviceProperties = new Hashtable();
		serviceProperties.put("osgi.jndi.url.scheme", "testURL");
		
		registerService(ObjectFactory.class.getName(), 
				 	    urlContextFactory, 
				 	    serviceProperties);

        // reference data does not matter, since we're testing that
        // the factory manager can locate the URL context factory 
		// to support this URL scheme
        Reference reference = new Reference("test", null, null);
        // create a string reference address of type URL
        // and add it to the reference
        reference.add(new StringRefAddr("URL", "testURL://testOne"));
        
        Object result = NamingManager.getObjectInstance(reference, null, null, null);
		assertEquals("Incorrect type returned by URL context factory",
					  new Integer(expectedValue), result);
	}
	
	
	/**
	 * Verify that if no ObjectFactories are found to resolve the reference, 
	 * then the Reference object itself is returned.  This behavior complies with the 
	 * behavior of the NamingManager.getObjectInstance() method.  In this test case
	 * the Reference specifies a factory class that is not available.    
	 * 
	 */
	public void testReturnValueWhenSpecifiedAndNoObjectFactoriesFound() throws Exception {
        // no ObjectFactory services or builder services are registered for this test
		Reference reference = new Reference("test", "com.test.factory.FactoryDoesNotExist", null);
        
        Object result = NamingManager.getObjectInstance(reference, null, null, null);
        assertSame("Factory Manager did not return the refInfo as expected",
        		    reference, result);
	}
	
	
	/**
	 * Verify that if no ObjectFactories are found to resolve the reference, 
	 * then the Reference object itself is returned.  This behavior complies with the 
	 * behavior of the NamingManager.getObjectInstance() method.  In this test case
	 * the Reference does not specify a factory class.      
	 * 
	 */
	public void testReturnValueWhenNotSpecifiedAndNoObjectFactoriesFound() throws Exception {
		// no ObjectFactory services or builder services are registered for this test
		Reference reference = new Reference("test", null, null);
		
		Object result = NamingManager.getObjectInstance(reference, null, null, null);
        assertSame("Factory Manager did not return the refInfo as expected",
        		    reference, result); 
	}
	
	
	/**
	 * Verify that an ObjectFactoryBuilder can be queried in the attempt 
	 * to resolve a reference.  Verify that this process works as expected 
	 * when the NamingManager.getObjectInstance() method is called.  
	 * @throws Exception
	 */
	public void testReferenceableSupportWithBuilder() throws Exception {
		final int expectedValue = 100;
		// stub builder to be used in test
		ObjectFactoryBuilder factoryBuilder = new ObjectFactoryBuilder() {
			public ObjectFactory createObjectFactory(Object var0, Hashtable var1) throws NamingException {
				return new ObjectFactory() {
				    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable environment) throws Exception {
				        return new Integer(expectedValue);
				    }
				};
			}
        };
        
        // only register the builder implementation
        registerService(ObjectFactoryBuilder.class.getName(), factoryBuilder, null);

        // create implementation of Referenceable that can
        // create a reference to resolve
        Referenceable referenceable = new Referenceable() {
			public Reference getReference() throws NamingException {
				return new Reference("test", "com.test.factory.DoesNotExist", null);
			}
        };
        
        Object result = NamingManager.getObjectInstance(referenceable, null, null, null);
        assertEquals("JNDI Factory Manager did not locate the correct ObjectFactory", 
                      new Integer(expectedValue), result);
	}
	
	
	/**
	 * Verify that the Gemini Naming bundle can support calls to resolve 
	 * a reference from the NamingManager.getObjectInstance() method.  This test
	 * verifies that an ObjectFactory registered as an OSGi service can be queried in
	 * the attempt to resolve the reference.  
	 */
	public void testReferenceableSupportWithObjectFactory() throws Exception {
		final int expectedValue = 100;
		// stub builder to be used in test
		final ObjectFactory objectFactory = new ObjectFactory() {
			public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable environment) throws Exception {
				return new Integer(expectedValue);
			}
		};
		
		
		String[] serviceInterfaces = { ObjectFactory.class.getName(), 
				                       objectFactory.getClass().getName() };

		ServiceRegistration serviceRegistration = 
			bundleContext.registerService(serviceInterfaces, objectFactory, null);
        
        try {
			// create implementation of Referenceable that can
			// create a reference to resolve
			Referenceable referenceable = new Referenceable() {
				public Reference getReference() throws NamingException {
					return new Reference("test", objectFactory.getClass().getName(), null);
				}
			};
			
			Object result = NamingManager.getObjectInstance(referenceable, null, null, null);
			assertEquals("JNDI Factory Manager did not locate the correct ObjectFactory", 
			              new Integer(expectedValue), result);
		}
		finally {
			serviceRegistration.unregister();
		}
	}
	
	
	/**
	 * Verify that a "jndi.properties" file can be used in a Bundle to 
	 * specify the desired InitialContextFactory implementation.  
	 */
	public void testJndiPropertiesFileFromArchive() throws Exception {
		final String expectedLookupName = "test-binding-one";
		final String expectedLookupValue = "this is only a test!";
		// setup a stub context factory
		Context testContext = new TestContext() {
			public Object lookup(String name) throws NamingException {
				if(name.equals(expectedLookupName)) {
					return expectedLookupValue;
				}
				
				throw new NameNotFoundException("Error in test");
			}
			
		};
		
		InitialContextFactory initialContextFactory = new TestContextFactory(testContext);
		String[] interfaceNames = { InitialContextFactory.class.getName(), TestContextFactory.class.getName() }; 
		
		// register context factory
		ServiceRegistration serviceRegistration = 
			bundleContext.registerService(interfaceNames, initialContextFactory, null);
		
		// create and setup a temp file for testing
		File jndiPropertiesFile = File.createTempFile("jndi", "properties");
		FileOutputStream outputStream = new FileOutputStream(jndiPropertiesFile);
		Properties tempProperties = new Properties();
		tempProperties.put(Context.INITIAL_CONTEXT_FACTORY, TestContextFactory.class.getName());
		tempProperties.store(outputStream, "test properties file");

		ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
		InitialContext context = null;
		try {
			URL testURL = 
				new URL("", "", -1, "", new TestURLStreamHandler(jndiPropertiesFile));
			
			final TestClassLoader testClassLoader = new TestClassLoader(testURL);
			// set context classloader that can provide the jndi.properties file
			Thread.currentThread().setContextClassLoader(testClassLoader);
			
			//System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "this.package.doesNotExist.Factory");
			Hashtable environment = new Hashtable();
			environment.put("osgi.service.jndi.bundleContext", bundleContext);
			context = new InitialContext(environment);
			assertEquals("Incorrect value returned by context factory", 
					     expectedLookupValue, context.lookup(expectedLookupName));
		} finally {
			context.close();
			
			serviceRegistration.unregister();
			
			// clean up context classloader
			Thread.currentThread().setContextClassLoader(oldClassLoader);
			
			if (jndiPropertiesFile != null) {
				// clean up temp file
				jndiPropertiesFile.delete();
			}
			
			// clean up system property
			Properties sysProperties = System.getProperties();
			sysProperties.remove(Context.INITIAL_CONTEXT_FACTORY);
		}
	}
    
    /**
     * Verify that RMI URL Context Factory can be used by default.
     */
    public void testRMIURLContextFactory() throws Exception {
        MBeanServer theMBeanServer = ManagementFactory.getPlatformMBeanServer();
		// Create the RMI registry
		try {
			LocateRegistry.createRegistry(1515);
		} catch (RemoteException e) {
			fail("Can't create registry: " + e.toString());
		}
        
		// Build the connection string with fixed ports
		StringBuilder url = new StringBuilder();
		url.append("service:jmx:rmi://localhost:");
		url.append(1516);
		url.append("/jndi/rmi://localhost:");
		url.append(1515);
		url.append("/jmxrmi");
		
        JMXServiceURL serviceUrl = null;
		try {
			serviceUrl = new JMXServiceURL(url.toString());
		} catch (MalformedURLException e) {
			fail("Can't create registry: " + e.toString());
		}
        
		Map<String, Object> env = new HashMap<String, Object>();
		ClassLoader currentTCCL = Thread.currentThread().getContextClassLoader();
        JMXConnectorServer cs = null;
		try {
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
			cs = JMXConnectorServerFactory.newJMXConnectorServer(serviceUrl, env, theMBeanServer);
			cs.start();
			System.out.println("Start MBean JMX registry with ports: registry=" + 1515 + ", server=" + 1516);
            assertTrue(cs.isActive());
		} catch (IOException e) {
			fail("Can't create registry: " + e.toString());
		} finally {
			Thread.currentThread().setContextClassLoader(currentTCCL);
            if (cs != null) {
                cs.stop();
            }
		}
    }
	
	// Stub implementations of JNDI factories used for simpler unit testing
	static class TestContextFactoryBuilder implements InitialContextFactoryBuilder {
		private final Context m_context;
		
		TestContextFactoryBuilder(Context context) {
			m_context = context;
		}
		
		
		public InitialContextFactory createInitialContextFactory(Hashtable var0)
				throws NamingException {
			return new TestContextFactory(m_context);
		}
	}
	
	private static class TestContextFactory implements InitialContextFactory {
		private final Context m_context;
		
		TestContextFactory(Context context) {
			m_context = context;
		}
		
		
		public Context getInitialContext(Hashtable var0) throws NamingException {
			return m_context;
		}
		
	}
	
	static class TestContext implements Context {
		
		private int m_numOfCloseCalls = 0;
		
		int getNumCloseCalls() {
			return m_numOfCloseCalls;
		}
		
		public Object addToEnvironment(String var0, Object var1)
				throws NamingException {
			return null;
		}

		public void bind(String var0, Object var1) throws NamingException {
		}

		public void bind(Name var0, Object var1) throws NamingException {
		}

		public void close() throws NamingException {
			m_numOfCloseCalls++;
		}

		public String composeName(String var0, String var1)
				throws NamingException {
			return null;
		}

		public Name composeName(Name var0, Name var1) throws NamingException {
			return null;
		}

		public Context createSubcontext(String var0) throws NamingException {
			return null;
		}

		public Context createSubcontext(Name var0) throws NamingException {
			return null;
		}

		public void destroySubcontext(String var0) throws NamingException {
		}

		public void destroySubcontext(Name var0) throws NamingException {
		}

		public Hashtable getEnvironment() throws NamingException {
			return null;
		}

		public String getNameInNamespace() throws NamingException {
			return null;
		}

		public NameParser getNameParser(String var0) throws NamingException {
			return null;
		}

		public NameParser getNameParser(Name var0) throws NamingException {
			return null;
		}

		public NamingEnumeration list(String var0) throws NamingException {
			return null;
		}

		public NamingEnumeration list(Name var0) throws NamingException {
			return null;
		}

		public NamingEnumeration listBindings(String var0)
				throws NamingException {
			return null;
		}

		public NamingEnumeration listBindings(Name var0) throws NamingException {
			return null;
		}

		public Object lookup(String var0) throws NamingException {
			return null;
		}

		public Object lookup(Name var0) throws NamingException {
			return null;
		}

		public Object lookupLink(String var0) throws NamingException {
			return null;
		}

		public Object lookupLink(Name var0) throws NamingException {
			return null;
		}

		public void rebind(String var0, Object var1) throws NamingException {
		}

		public void rebind(Name var0, Object var1) throws NamingException {
		}

		public Object removeFromEnvironment(String var0) throws NamingException {
			return null;
		}

		public void rename(String var0, String var1) throws NamingException {
		}

		public void rename(Name var0, Name var1) throws NamingException {
		}

		public void unbind(String var0) throws NamingException {
		}

		public void unbind(Name var0) throws NamingException {
		}
		
	}
	
	
	private static class TestURLStreamHandler extends URLStreamHandler {
		private final File m_propertiesFile;
		
		TestURLStreamHandler(File propertiesFile) {
			m_propertiesFile = propertiesFile;
		}
		protected URLConnection openConnection(URL var0) throws IOException {
			return new URLConnection(null) {
				public void connect() throws IOException {
					// no-op 
				}

				public Object getContent() throws IOException {
					return m_propertiesFile;
				}
			};
		}
		
	}
	
	private static class TestClassLoader extends ClassLoader implements BundleReference {

		private final URL m_fileURL;
		
		TestClassLoader(URL fileURL) {
			m_fileURL = fileURL;
		}
		
		public Bundle getBundle() {
			return new TestBundle() {
				public URL getResource(String name) {
					if(name.equals("jndi.properties")) {
						return m_fileURL;
					} 
					
					return null;
				}
				
			};
		}
		
	}
	
	private static class TestBundle implements Bundle {

		public Enumeration findEntries(String path, String filePattern,
				boolean recurse) {
			return null;
		}

		public BundleContext getBundleContext() {
			return null;
		}

		public long getBundleId() {
			return 0;
		}

		public URL getEntry(String path) {
			return null;
		}

		public Enumeration getEntryPaths(String path) {
			return null;
		}

		public Dictionary getHeaders() {
			return null;
		}

		public Dictionary getHeaders(String locale) {
			return null;
		}

		public long getLastModified() {
			return 0;
		}

		public String getLocation() {
			return null;
		}

		public ServiceReference[] getRegisteredServices() {
			return null;
		}

		public URL getResource(String name) {
			return null;
		}

		public Enumeration getResources(String name) throws IOException {
			return null;
		}

		public ServiceReference[] getServicesInUse() {
			return null;
		}

		public Map getSignerCertificates(int signersType) {
			return null;
		}

		public int getState() {
			return 0;
		}

		public String getSymbolicName() {
			return null;
		}

		public Version getVersion() {
			return null;
		}

		public boolean hasPermission(Object permission) {
			return false;
		}

		public Class loadClass(String name) throws ClassNotFoundException {
			return null;
		}

		public void start() throws BundleException {
		}

		public void start(int options) throws BundleException {
		}

		public void stop() throws BundleException {
		}

		public void stop(int options) throws BundleException {
		}

		public void uninstall() throws BundleException {
		}

		public void update() throws BundleException {
		}

		public void update(InputStream input) throws BundleException {
		}
		
	}
	

	private class TestClassLoaderTwo extends ClassLoader implements BundleReference {

		public Bundle getBundle() {
			return new Bundle() {
				public Enumeration findEntries(String path, String filePattern, boolean recurse) {
					return null;
				}

				public BundleContext getBundleContext() {
					return getContext();
				}

				public long getBundleId() {
					return 0;
				}

				public URL getEntry(String path) {
					return null;
				}

				public Enumeration getEntryPaths(String path) {
					return null;
				}

				public Dictionary getHeaders() {
					return null;
				}

				public Dictionary getHeaders(String locale) {
					return null;
				}

				public long getLastModified() {
					return 0;
				}

				public String getLocation() {
					return null;
				}

				public ServiceReference[] getRegisteredServices() {
					return null;
				}

				public URL getResource(String name) {
					return null;
				}

				public Enumeration getResources(String name) throws IOException {
					return null;
				}

				public ServiceReference[] getServicesInUse() {
					return null;
				}

				public Map getSignerCertificates(int signersType) {
					return null;
				}

				public int getState() {
					return 0;
				}

				public String getSymbolicName() {
					return null;
				}

				public Version getVersion() {
					return null;
				}

				public boolean hasPermission(Object permission) {
					return false;
				}

				public Class loadClass(String name) throws ClassNotFoundException {
					return null;
				}

				public void start() throws BundleException {
				}

				public void start(int options) throws BundleException {
				}

				public void stop() throws BundleException {
				}

				public void stop(int options) throws BundleException {
				}

				public void uninstall() throws BundleException {
				}

				public void update() throws BundleException {
				}

				public void update(InputStream input) throws BundleException {
				}
				
			};
		}
			
	}
	
	
}
