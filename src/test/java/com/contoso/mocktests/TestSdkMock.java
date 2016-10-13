/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 * See License.txt in the project root for license information.
 */

package com.contoso.mocktests;


import com.contoso.helpers.HelperUtils;
import com.microsoft.azure.datalake.store.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;


import java.io.IOException;
import java.util.Properties;

public class TestSdkMock {
    private String directory = "/mockTestDirectory";
    private ADLStoreClient client = null;
    private boolean testsEnabled = true;
    MockWebServer server = null;

    @Before
    public void setup() throws IOException {
        Properties prop = HelperUtils.getProperties();

        server = new MockWebServer();
        QueueDispatcher dispatcher = new QueueDispatcher();
        dispatcher.setFailFast(new MockResponse().setResponseCode(400));
        server.setDispatcher(dispatcher);
        server.start();
        String accountFQDN = server.getHostName() + ":" + server.getPort();
        String dummyToken = "testDummyAadToken";

        client = ADLStoreClient.createClient(accountFQDN, dummyToken);
        client.setOptions(new ADLStoreOptions().setInsecureTransport());

        testsEnabled = Boolean.parseBoolean(prop.getProperty("MockTestsEnabled", "true"));
    }

    @After
    public void teardown() throws IOException {
        server.shutdown();
    }

    @Test
    public void testExponentialRetryTiming() throws IOException {
        Assume.assumeTrue(testsEnabled);
        String filename = directory + "/" + "Mock.testExponentialRetryTiming";

        MockResponse gfsResponse = (new MockResponse()).setResponseCode(503);
        server.enqueue(gfsResponse);  // original failure
        server.enqueue(gfsResponse);  // first retry
        server.enqueue(gfsResponse);  // second retry
        server.enqueue(gfsResponse);  // third retry
        server.enqueue(gfsResponse);  // fourth retry

        long start = System.currentTimeMillis();
        try {
            client.getDirectoryEntry(filename);
            fail("should have received 503 exception");
        } catch (ADLException ex) {
            assertTrue("error should be 503", ex.httpResponseCode == 503);
            assertTrue("retries should be 4", ex.numRetries == 4);
        }
        long end = System.currentTimeMillis();

        long duration = end - start;
        long expectedDuration = 85 * 1000;
        long expectedDurationMin = expectedDuration - 1000;
        long expectedDurationMax = expectedDuration + 1000;

        assertTrue("Total time was more than max duration expected", expectedDuration < expectedDurationMax);
        assertTrue("Total time was less than min duration expected", expectedDuration > expectedDurationMin);
    }

    @Test
    public void test500Then200Pattern() throws IOException {
        Assume.assumeTrue(testsEnabled);
        String filename = directory + "/" + "Mock.test500Then200Pattern";

        server.enqueue(new MockResponse().setResponseCode(200)); // the first empty CREATE request
        ADLFileOutputStream os = client.createFile(filename, IfExists.OVERWRITE);
        String s = "Test string with data\n";
        byte[] data = s.getBytes();

        server.enqueue(new MockResponse().setResponseCode(200)); // an append that succeeds
        os.write(data);
        os.flush();

        server.enqueue(new MockResponse().setResponseCode(500)); // second append that should fail
        server.enqueue(new MockResponse().setResponseCode(200)); // succeed on retry
        try {
            os.write(data);
            os.flush();
        } catch (IOException ex) {
            fail("should not get here - request should succeed on retry");
        }

        server.enqueue(new MockResponse().setResponseCode(200)); // succeed for the 0-length append
        os.close();
    }


    @Test
    public void testConnectionReset() throws IOException {
        Assume.assumeTrue(false);  // test half-done
        String filename = directory + "/" + "Mock.testConnectionReset";

        server.enqueue(new MockResponse().setResponseCode(200)); // the first empty CREATE request
        ADLFileOutputStream os = client.createFile(filename, IfExists.OVERWRITE);
        String s = "Test string with data\n";
        byte[] data = s.getBytes();

        server.enqueue(new MockResponse().setResponseCode(200)); // an append that succeeds
        os.write(data);
        os.flush();

        server.enqueue(new MockResponse().setResponseCode(500)); // second append that should fail
        server.enqueue(new MockResponse().setResponseCode(200)); // succeed on retry
        //new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);

        try {
            os.write(data);
            os.flush();
        } catch (IOException ex) {
            fail("should not get here - request should succeed on retry");
        }

        server.enqueue(new MockResponse().setResponseCode(200)); // succeed for the 0-length append
        os.close();
    }

}
