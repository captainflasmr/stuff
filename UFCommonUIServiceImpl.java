package se.pitch.oss.fedpro.cuis.server.grpc;

import com.baesystems.mimesis.Success;
import com.baesystems.mimesis.UFCommonUIServiceGrpc;
import com.baesystems.mimesis.UfCuiHlaServer.*;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.pitch.oss.fedpro.cuis.server.rti.RtiAdapter;
import se.pitch.oss.fedpro.cuis.server.session.ClientSession;
import se.pitch.oss.fedpro.cuis.server.session.SessionRegistry;

/**
 * gRPC service implementation for UFCommonUIService.
 * Sub-Apps call these RPCs to interact with the HLA RTI through the CUIS proxy.
 *
 * <p>Object and interaction classes are addressed by name; the {@link RtiAdapter}
 * resolves names to HLA handles. Each RPC returns a per-call response message
 * carrying a {@link Success} status.
 */
public class UFCommonUIServiceImpl extends UFCommonUIServiceGrpc.UFCommonUIServiceImplBase {

   private static final Logger _logger = LoggerFactory.getLogger(UFCommonUIServiceImpl.class);

   private final SessionRegistry _sessionRegistry;
   private final RtiAdapter _rtiAdapter;

   public UFCommonUIServiceImpl(SessionRegistry sessionRegistry, RtiAdapter rtiAdapter)
   {
      _sessionRegistry = sessionRegistry;
      _rtiAdapter = rtiAdapter;
   }

   // ==================== Client registration ====================

   @Override
   public void registerClient(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver)
   {
      _logger.info("Registering client at {}:{} (littleEndian={})",
            request.getClientAddr(), request.getClientPortNumber(), request.getLittleEndian());

      ClientSession session = _sessionRegistry.register(
            request.getClientAddr(),
            request.getClientPortNumber(),
            request.getLittleEndian());

      complete(responseObserver, RegisterResponse.newBuilder()
            .setSuccessful(ok())
            .setTheClientId(Client_Id.newBuilder().setId(session.getClientId()).build())
            .build());
   }

   @Override
   public void removeClient(RemoveRequest request, StreamObserver<RemoveResponse> responseObserver)
   {
      int clientId = request.getTheClientId().getId();
      _logger.info("Removing client {}", clientId);
      _sessionRegistry.unregister(clientId);
      complete(responseObserver, RemoveResponse.newBuilder().setSuccessful(ok()).build());
   }

   // ==================== Object class methods ====================

   @Override
   public void publishObjectAttributes(PublishObjectRequest request, StreamObserver<PublishObjectResponse> responseObserver)
   {
      PublishObjectResponse.Builder response = PublishObjectResponse.newBuilder();
      try {
         ClientSession session = requireClient(request.getTheClientId());
         byte[] classHandle = _rtiAdapter.publishObjectAttributes(request.getName(), request.getAttrValListList());
         session.addObjectPublication(request.getName());
         response.setSuccessful(ok()).setObjectHandle(ByteString.copyFrom(classHandle));
      } catch (Exception e) {
         response.setSuccessful(error(e));
      }
      complete(responseObserver, response.build());
   }

   @Override
   public void unpublishObject(UnpublishObjectRequest request, StreamObserver<UnPublishObjectResponse> responseObserver)
   {
      UnPublishObjectResponse.Builder response = UnPublishObjectResponse.newBuilder();
      try {
         ClientSession session = requireClient(request.getTheClientId());
         _rtiAdapter.unpublishObject(request.getName(), request.getAttrListList());
         if (request.getAttrListCount() == 0) {
            session.removeObjectPublication(request.getName());
         }
         response.setSuccessful(ok());
      } catch (Exception e) {
         response.setSuccessful(error(e));
      }
      complete(responseObserver, response.build());
   }

   @Override
   public void subscribeObjectAttributes(SubscribeObjectRequest request, StreamObserver<SubscribeObjectResponse> responseObserver)
   {
      SubscribeObjectResponse.Builder response = SubscribeObjectResponse.newBuilder();
      try {
         ClientSession session = requireClient(request.getTheClientId());
         byte[] classHandle = _rtiAdapter.subscribeObjectAttributes(request.getName(), request.getAttrListList());
         session.addObjectSubscription(_rtiAdapter.canonicalObjectClassName(request.getName()));
         response.setSuccessful(ok()).setObjectHandle(ByteString.copyFrom(classHandle));
      } catch (Exception e) {
         response.setSuccessful(error(e));
      }
      complete(responseObserver, response.build());
   }

   @Override
   public void unsubscribeObject(UnsubscribeObjectRequest request, StreamObserver<UnsubscribeObjectResponse> responseObserver)
   {
      UnsubscribeObjectResponse.Builder response = UnsubscribeObjectResponse.newBuilder();
      try {
         ClientSession session = requireClient(request.getTheClientId());
         _rtiAdapter.unsubscribeObject(request.getName(), request.getAttrListList());
         if (request.getAttrListCount() == 0) {
            session.removeObjectSubscription(_rtiAdapter.canonicalObjectClassName(request.getName()));
         }
         response.setSuccessful(ok());
      } catch (Exception e) {
         response.setSuccessful(error(e));
      }
      complete(responseObserver, response.build());
   }

   @Override
   public void updateAttributeValues(UpdateObjectAttributeValuesRequest request, StreamObserver<UpdateObjectAttributeValuesResponse> responseObserver)
   {
      UpdateObjectAttributeValuesResponse.Builder response = UpdateObjectAttributeValuesResponse.newBuilder();
      try {
         requireClient(request.getTheClientId());
         _rtiAdapter.updateAttributeValues(
               request.getInstanceHandle().toByteArray(),
               request.getAttrValListList(),
               tagOrNull(request.getUserSuppliedTag()));
         response.setSuccessful(ok());
      } catch (Exception e) {
         response.setSuccessful(error(e));
      }
      complete(responseObserver, response.build());
   }

   @Override
   public void requestAttributeValueUpdate(RequestObjectUpdateRequest request, StreamObserver<RequestObjectUpdateResponse> responseObserver)
   {
      RequestObjectUpdateResponse.Builder response = RequestObjectUpdateResponse.newBuilder();
      try {
         requireClient(request.getTheClientId());
         byte[] instanceHandle = request.getScopeCase() == RequestObjectUpdateRequest.ScopeCase.INSTANCE_HANDLE
               ? request.getInstanceHandle().toByteArray()
               : null;
         _rtiAdapter.requestObjectUpdate(
               request.getObjectName(),
               instanceHandle,
               request.getAttrListList(),
               tagOrNull(request.getUserSuppliedTag()));
         response.setSuccessful(ok());
      } catch (Exception e) {
         response.setSuccessful(error(e));
      }
      complete(responseObserver, response.build());
   }

   // ==================== Interaction class methods ====================

   @Override
   public void publishInteraction(PublishInteractionRequest request, StreamObserver<PublishInteractionResponse> responseObserver)
   {
      PublishInteractionResponse.Builder response = PublishInteractionResponse.newBuilder();
      try {
         ClientSession session = requireClient(request.getTheClientId());
         _rtiAdapter.publishInteraction(request.getName());
         session.addInteractionPublication(request.getName());
         response.setSuccessful(ok());
      } catch (Exception e) {
         response.setSuccessful(error(e));
      }
      complete(responseObserver, response.build());
   }

   @Override
   public void unpublishInteraction(UnpublishInteractionRequest request, StreamObserver<UnpublishInteractionResponse> responseObserver)
   {
      UnpublishInteractionResponse.Builder response = UnpublishInteractionResponse.newBuilder();
      try {
         ClientSession session = requireClient(request.getTheClientId());
         _rtiAdapter.unpublishInteraction(request.getName());
         session.removeInteractionPublication(request.getName());
         response.setSuccessful(ok());
      } catch (Exception e) {
         response.setSuccessful(error(e));
      }
      complete(responseObserver, response.build());
   }

   @Override
   public void subscribeInteraction(SubscribeInteractionRequest request, StreamObserver<SubscribeInteractionResponse> responseObserver)
   {
      SubscribeInteractionResponse.Builder response = SubscribeInteractionResponse.newBuilder();
      try {
         ClientSession session = requireClient(request.getTheClientId());
         _rtiAdapter.subscribeInteraction(request.getName());
         session.addInteractionSubscription(_rtiAdapter.canonicalInteractionClassName(request.getName()));
         response.setSuccessful(ok());
      } catch (Exception e) {
         response.setSuccessful(error(e));
      }
      complete(responseObserver, response.build());
   }

   @Override
   public void unsubscribeInteraction(UnsubscribeInteractionRequest request, StreamObserver<UnsubscribeInteractionResponse> responseObserver)
   {
      UnsubscribeInteractionResponse.Builder response = UnsubscribeInteractionResponse.newBuilder();
      try {
         ClientSession session = requireClient(request.getTheClientId());
         _rtiAdapter.unsubscribeInteraction(request.getName());
         session.removeInteractionSubscription(_rtiAdapter.canonicalInteractionClassName(request.getName()));
         response.setSuccessful(ok());
      } catch (Exception e) {
         response.setSuccessful(error(e));
      }
      complete(responseObserver, response.build());
   }

   @Override
   public void sendInteraction(SendInteractionRequest request, StreamObserver<SendInteractionResponse> responseObserver)
   {
      SendInteractionResponse.Builder response = SendInteractionResponse.newBuilder();
      try {
         requireClient(request.getTheClientId());
         _rtiAdapter.sendInteraction(
               request.getName(),
               request.getParamValListList(),
               tagOrNull(request.getUserSuppliedTag()));
         response.setSuccessful(ok());
      } catch (Exception e) {
         response.setSuccessful(error(e));
      }
      complete(responseObserver, response.build());
   }

   // ==================== Helpers ====================

   private ClientSession requireClient(Client_Id clientId)
   {
      ClientSession session = _sessionRegistry.getSession(clientId.getId());
      if (session == null) {
         throw new IllegalArgumentException("Unknown client id: " + clientId.getId());
      }
      return session;
   }

   private static Success ok()
   {
      return Success.newBuilder().setSuccessful(true).build();
   }

   private Success error(Exception e)
   {
      _logger.error("RPC error: {}", e.getMessage(), e);
      return Success.newBuilder()
            .setSuccessful(false)
            .setErrorCode(1)
            .setErrorText(e.getMessage() == null ? e.toString() : e.getMessage())
            .build();
   }

   private static byte[] tagOrNull(ByteString tag)
   {
      return tag.isEmpty() ? null : tag.toByteArray();
   }

   private static <T> void complete(StreamObserver<T> observer, T response)
   {
      observer.onNext(response);
      observer.onCompleted();
   }
}
