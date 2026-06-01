package se.pitch.oss.fedpro.cuis.test.demo;

import se.pitch.oss.fedpro.cuis.server.CuisMain;
import se.pitch.oss.fedpro.cuis.test.mock.MockRTIambassador;

/**
 * Standalone CUIS launcher intended to be driven by grpcui / grpcurl.
 *
 * Unlike {@link CuisDemo}, this does NOT run any scripted scenario and does NOT
 * start any sub-app simulators. It just brings up the CUIS gRPC server (backed
 * by a {@link MockRTIambassador}) on a fixed, predictable port and blocks. That
 * makes grpcui the "sub-app": every call you make in the grpcui web UI is an
 * RPC into the CUIS UFCommonUIService, exactly as a real Web Sub-App would make.
 *
 * Run with:
 *   ./gradlew :cuis-test:runGrpcuiServer --console=plain
 *   ./gradlew :cuis-test:runGrpcuiServer --console=plain -Pport=50051
 *
 * Then, in another terminal:
 *   grpcui -plaintext localhost:50051
 *
 * Because the CUIS server registers the gRPC reflection service, grpcui needs
 * no .proto files - it discovers UFCommonUIService and all message types live.
 *
 * Suggested call order in grpcui (UFCommonUIService):
 *   1. Register_Client          -> returns the_client_id (note the id)
 *   2. Publish_Object_Attributes / Subscribe_Object_Attributes  (name = "Vehicle", attrs Position/Speed)
 *   3. Publish_Interaction / Subscribe_Interaction              (name = "Alert")
 *   4. Update_Attribute_Values / Send_Interaction
 *
 * Note on callbacks: grpcui can only act as a gRPC *client*, so it drives the
 * inbound half of a sub-app. The outbound callbacks CUIS makes back to a sub-app
 * (Discover_Object_Instance, Reflect_Attribute_Values, Receive_Interaction on
 * UFCommonUIClientService) require the sub-app to run its own gRPC server, which
 * grpcui cannot do. To watch those callbacks too, use {@link CuisDemo} or
 * SubAppSimulator. This launcher is for stimulating the CUIS server itself.
 */
public class CuisGrpcuiServer {

   private static final int DEFAULT_PORT = 50051;

   public static void main(String[] args) throws Exception
   {
      int port = DEFAULT_PORT;
      if (args.length > 0) {
         port = Integer.parseInt(args[0].trim());
      }

      // A mock RTI so publish/subscribe/update/send all succeed without a real RTI.
      // Pre-register the classes the FOM would normally define, so name-based
      // publish/subscribe calls from grpcui resolve to handles.
      MockRTIambassador mockRti = new MockRTIambassador();
      mockRti.preRegisterObjectClass("Vehicle", "Position", "Speed");
      mockRti.preRegisterInteractionClass("Alert", "Message", "Severity");

      CuisMain cuis = new CuisMain(mockRti, port);
      cuis.start("DemoFederation", new String[] { "demo.xml" });
      int actualPort = cuis.getGrpcServer().getPort();

      System.out.println();
      System.out.println("CUIS gRPC server is up (reflection enabled).");
      System.out.println("  Pre-registered object class : Vehicle [Position, Speed]");
      System.out.println("  Pre-registered interaction   : Alert   [Message, Severity]");
      System.out.println();
      System.out.println("Drive it as a simulated sub-app with:");
      System.out.println("    grpcui   -plaintext localhost:" + actualPort);
      System.out.println("    grpcurl  -plaintext localhost:" + actualPort + " list");
      System.out.println("    grpcurl  -plaintext localhost:" + actualPort + " describe cui_hla_server.UFCommonUIService");
      System.out.println();
      System.out.println("Press Ctrl+C to stop.");

      Runtime.getRuntime().addShutdownHook(new Thread(cuis::stop));
      cuis.awaitTermination();
   }
}
