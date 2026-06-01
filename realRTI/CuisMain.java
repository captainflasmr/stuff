package se.pitch.oss.fedpro.cuis.server;

import hla.rti1516_2025.RTIambassador;
import hla.rti1516_2025.RtiConfiguration;
import hla.rti1516_2025.RtiFactory;
import hla.rti1516_2025.RtiFactoryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.pitch.oss.fedpro.cuis.server.grpc.CuisGrpcServer;
import se.pitch.oss.fedpro.cuis.server.grpc.UFCommonUIServiceImpl;
import se.pitch.oss.fedpro.cuis.server.routing.RoutingEngine;
import se.pitch.oss.fedpro.cuis.server.rti.RtiAdapter;
import se.pitch.oss.fedpro.cuis.server.session.SessionRegistry;

/**
 * Entry point for the Common UI Server proxy federate.
 *
 * Wires together all CUIS components:
 * - SessionRegistry: tracks connected sub-app clients
 * - RoutingEngine: dispatches HLA callbacks to subscribed clients
 * - RtiAdapter: bridges HLA RTIambassador and FederateAmbassador
 * - CuisGrpcServer: exposes UFCommonUIService to sub-apps
 *
 * For production use, obtain RTIambassador via:
 *   RtiFactory factory = RtiFactoryFactory.getRtiFactory("Federate Protocol");
 *   RTIambassador rti = factory.getRtiAmbassador();
 *
 * For testing, inject a MockRTIambassador directly.
 */
public class CuisMain {

   private static final Logger _logger = LoggerFactory.getLogger(CuisMain.class);
   private static final int DEFAULT_GRPC_PORT = 50051;

   private final SessionRegistry _sessionRegistry;
   private final RoutingEngine _routingEngine;
   private final RtiAdapter _rtiAdapter;
   private final CuisGrpcServer _grpcServer;

   public CuisMain(RTIambassador rtiAmbassador, int grpcPort)
   {
      _sessionRegistry = new SessionRegistry();
      _routingEngine = new RoutingEngine(_sessionRegistry);
      _rtiAdapter = new RtiAdapter(rtiAmbassador, _routingEngine);

      UFCommonUIServiceImpl serviceImpl = new UFCommonUIServiceImpl(_sessionRegistry, _rtiAdapter);
      _grpcServer = new CuisGrpcServer(grpcPort, serviceImpl);
   }

   public SessionRegistry getSessionRegistry()
   {
      return _sessionRegistry;
   }

   public RtiAdapter getRtiAdapter()
   {
      return _rtiAdapter;
   }

   public CuisGrpcServer getGrpcServer()
   {
      return _grpcServer;
   }

   public void start(String federationName, String[] fomModules) throws Exception
   {
      start(federationName, fomModules, null);
   }

   public void start(String federationName, String[] fomModules, RtiConfiguration configuration)
         throws Exception
   {
      _rtiAdapter.connectAndJoin(federationName, fomModules, configuration);
      _grpcServer.start();
      _logger.info("CUIS started. Federation: {}", federationName);
   }

   public void stop()
   {
      _grpcServer.stop();
      _routingEngine.shutdown();
      _sessionRegistry.shutdownAll();
      _rtiAdapter.disconnect();
      _logger.info("CUIS stopped.");
   }

   public void awaitTermination() throws InterruptedException
   {
      _grpcServer.awaitTermination();
   }

   /**
    * Production entry point: connects to a real RTI through the Federate
    * Protocol client, which in turn talks to a Federate Protocol Server
    * (e.g. the one bundled with Pitch pRTI).
    *
    * <p>Configuration is taken from environment variables, with command-line
    * arguments overriding them, so the same launcher works from a shell or a
    * container without code changes:
    *
    * <pre>
    *   args[0] / CUIS_FEDERATION  federation execution name   (default "CUIS")
    *   args[1] / CUIS_FOM         path to the FOM module XML   (default "CuisFom.xml")
    *   args[2] / CUIS_RTI_ADDRESS FedPro server address        (default "localhost:15164")
    *   args[3] / CUIS_GRPC_PORT   gRPC port for Sub-Apps       (default 50051)
    * </pre>
    *
    * Additional Federate Protocol / LRC settings can be supplied through the
    * standard {@code FEDPRO_CLIENT_SETTINGS} environment variable (see
    * doc/Settings.md); they need no special handling here.
    */
   public static void main(String[] args)
   {
      String federationName = arg(args, 0, "CUIS_FEDERATION", "CUIS");
      String fomPath = arg(args, 1, "CUIS_FOM", "CuisFom.xml");
      String rtiAddress = arg(args, 2, "CUIS_RTI_ADDRESS", "localhost:15164");
      int grpcPort = Integer.parseInt(arg(args, 3, "CUIS_GRPC_PORT", String.valueOf(DEFAULT_GRPC_PORT)));

      CuisMain cuis = null;
      try {
         RtiFactory factory = RtiFactoryFactory.getRtiFactory("Federate Protocol");
         RTIambassador rtiAmbassador = factory.getRtiAmbassador();

         RtiConfiguration configuration = RtiConfiguration.createConfiguration()
               .withRtiAddress(rtiAddress);

         cuis = new CuisMain(rtiAmbassador, grpcPort);

         final CuisMain running = cuis;
         Runtime.getRuntime().addShutdownHook(new Thread(running::stop, "cuis-shutdown"));

         _logger.info("Starting CUIS: federation='{}', fom='{}', rtiAddress='{}', grpcPort={}",
               federationName, fomPath, rtiAddress, grpcPort);
         cuis.start(federationName, new String[] {fomPath}, configuration);
         cuis.awaitTermination();
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      } catch (Exception e) {
         _logger.error("CUIS failed to start", e);
         if (cuis != null) {
            cuis.stop();
         }
         System.exit(1);
      }
   }

   /** Resolve a setting from args[index], else the named env var, else the default. */
   private static String arg(String[] args, int index, String envVar, String defaultValue)
   {
      if (args.length > index && !args[index].isEmpty()) {
         return args[index];
      }
      String env = System.getenv(envVar);
      return (env != null && !env.isEmpty()) ? env : defaultValue;
   }
}
