/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package com.reenchant.awsiot;

import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotMessage;
import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.services.iot.client.AWSIotTopic;
import com.amazonaws.services.iot.client.sample.sampleUtil.CommandArguments;
import com.amazonaws.services.iot.client.sample.sampleUtil.SampleUtil;
import org.json.JSONArray;
import org.json.JSONObject;

public class App {

    private static AWSIotMqttClient awsIotClient;

    public static void main(String[] args) throws AWSIotException {
        CommandArguments arguments = CommandArguments.parse(args);
        initClient(arguments);

        // Instantiate the IoT Agent with your AWS credentials
        final String thingName = SampleUtil.getConfig("thingName");
        final String tunnelNotificationTopic = String.format("$aws/things/%s/tunnels/notify", thingName);
        try {
            awsIotClient.connect();
            final TunnelNotificationListener listener = new TunnelNotificationListener(tunnelNotificationTopic);
            awsIotClient.subscribe(listener, true);
            App app = new App();
            app.waitMethod();
        }
        finally {
            awsIotClient.disconnect();
        }
    }

    private synchronized void waitMethod() {

        while (true) {
            //System.out.println("always running program ==> " + Calendar.getInstance().getTime());
            try {
                this.wait(2000);
            } catch (InterruptedException e) {

                e.printStackTrace();
            }
        }

    }

    private static void initClient(CommandArguments arguments) {
        String clientEndpoint = arguments.getNotNull("clientEndpoint", SampleUtil.getConfig("clientEndpoint"));
        String clientId = arguments.getNotNull("clientId", SampleUtil.getConfig("clientId"));

        String certificateFile = arguments.get("certificateFile", SampleUtil.getConfig("certificateFile"));
        String privateKeyFile = arguments.get("privateKeyFile", SampleUtil.getConfig("privateKeyFile"));
        if (awsIotClient == null && certificateFile != null && privateKeyFile != null) {
            String algorithm = arguments.get("keyAlgorithm", SampleUtil.getConfig("keyAlgorithm"));

            SampleUtil.KeyStorePasswordPair pair = SampleUtil.getKeyStorePasswordPair(certificateFile, privateKeyFile, algorithm);

            awsIotClient = new AWSIotMqttClient(clientEndpoint, clientId, pair.keyStore, pair.keyPassword);
        }

        if (awsIotClient == null) {
            String awsAccessKeyId = arguments.get("awsAccessKeyId", SampleUtil.getConfig("awsAccessKeyId"));
            String awsSecretAccessKey = arguments.get("awsSecretAccessKey", SampleUtil.getConfig("awsSecretAccessKey"));
            String sessionToken = arguments.get("sessionToken", SampleUtil.getConfig("sessionToken"));

            if (awsAccessKeyId != null && awsSecretAccessKey != null) {
                awsIotClient = new AWSIotMqttClient(clientEndpoint, clientId, awsAccessKeyId, awsSecretAccessKey,
                        sessionToken);
            }
        }

        if (awsIotClient == null) {
            throw new IllegalArgumentException("Failed to construct client due to missing certificate or credentials.");
        }
    }

    private static class TunnelNotificationListener extends AWSIotTopic {
        public TunnelNotificationListener(String topic) {
            super(topic);
        }

        @Override
        public void onMessage(AWSIotMessage message) {
            try {
                // Deserialize the MQTT message
                final JSONObject json = new JSONObject(message.getStringPayload());

                final String accessToken = json.getString("clientAccessToken");
                final String region = json.getString("region");

                final String clientMode = json.getString("clientMode");
                if (!clientMode.equals("destination")) {
                    throw new RuntimeException("Client mode " + clientMode + " in the MQTT message is not expected");
                }

                final JSONArray servicesArray = json.getJSONArray("services");
                if (servicesArray.length() > 1) {
                    throw new RuntimeException("Services in the MQTT message has more than 1 service");
                }
                final String service = servicesArray.get(0).toString();
                if (!service.equals("SSH")) {
                    throw new RuntimeException("Service " + service + " is not supported");
                }

                // Start the destination local proxy in a separate process to connect to the SSH Daemon listening port 22
                final ProcessBuilder pb = new ProcessBuilder("localproxy",
                        "-t", accessToken,
                        "-r", region,
                        "-d", "localhost:22");
                pb.start();
            }
            catch (Exception e) {
                //log.error("Failed to start the local proxy", e);
                System.out.println("Failed to start the local proxy!");
            }
        }
    }
}
