package se.pitch.oss.fedpro.cuis.server.rti;

import com.baesystems.mimesis.HlaCommon.Attribute_Values;
import com.baesystems.mimesis.HlaCommon.Attributes;
import com.baesystems.mimesis.HlaCommon.Parameter_Values;
import com.baesystems.mimesis.UfHlaCuiClient.DiscoverInstanceRequest;
import com.baesystems.mimesis.UfHlaCuiClient.ProvideAttributeValueUpdateRequest;
import com.baesystems.mimesis.UfHlaCuiClient.ReceiveInteractionRequest;
import com.baesystems.mimesis.UfHlaCuiClient.ReflectValuesRequest;
import com.baesystems.mimesis.UfHlaCuiClient.RemoveInstanceRequest;
import com.google.protobuf.ByteString;
import hla.rti1516_2025.*;
import hla.rti1516_2025.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.pitch.oss.fedpro.cuis.server.routing.RoutingEngine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core bridge between HLA RTI and the CUIS gRPC layer.
 *
 * <p>Wraps an RTIambassador instance and implements FederateAmbassador
 * to receive callbacks and route them to Sub-Apps via the RoutingEngine.
 *
 * <p>The Sub-App facing API identifies object/interaction classes by
 * <em>name</em>; this adapter resolves names to HLA handles on the inbound
 * path and resolves handles back to names on the outbound (callback) path.
 * Attribute/parameter values are encoded to/from HLA wire bytes via
 * {@link DataValueCodec}.
 */
public class RtiAdapter extends NullFederateAmbassador {

   private static final Logger _logger = LoggerFactory.getLogger(RtiAdapter.class);

   private final RTIambassador _rtiAmbassador;
   private final RoutingEngine _routingEngine;

   // Tracks object instance -> object class handle (both as encoded bytes) for routing.
   private final Map<ByteString, ByteString> _instanceToClassMap = new ConcurrentHashMap<>();

   public RtiAdapter(RTIambassador rtiAmbassador, RoutingEngine routingEngine)
   {
      _rtiAmbassador = rtiAmbassador;
      _routingEngine = routingEngine;
   }

   public RTIambassador getRtiAmbassador()
   {
      return _rtiAmbassador;
   }

   /**
    * Connect to RTI, create/join federation, using default connection
    * settings (FedPro server on localhost:15164). Suitable for tests with an
    * injected mock, which ignores the connection configuration.
    */
   public void connectAndJoin(String federationName, String[] fomModules)
         throws Exception
   {
      connectAndJoin(federationName, fomModules, null);
   }

   /**
    * Connect to RTI, create/join federation.
    *
    * @param configuration connection settings (e.g. the Federate Protocol
    *                      server address via {@code withRtiAddress}); when
    *                      {@code null}, connection defaults are used.
    */
   public void connectAndJoin(String federationName, String[] fomModules, RtiConfiguration configuration)
         throws Exception
   {
      if (configuration != null) {
         _rtiAmbassador.connect(this, CallbackModel.HLA_IMMEDIATE, configuration);
      } else {
         _rtiAmbassador.connect(this, CallbackModel.HLA_IMMEDIATE);
      }
      try {
         _rtiAmbassador.createFederationExecution(federationName, fomModules);
         _logger.info("Created federation: {}", federationName);
      } catch (FederationExecutionAlreadyExists e) {
         _logger.info("Federation already exists: {}", federationName);
      }
      _rtiAmbassador.joinFederationExecution("CUIS", federationName);
      _logger.info("Joined federation: {}", federationName);
   }

   /**
    * Resign and disconnect from RTI.
    */
   public void disconnect()
   {
      try {
         _rtiAmbassador.resignFederationExecution(ResignAction.DELETE_OBJECTS_THEN_DIVEST);
      } catch (Exception e) {
         _logger.warn("Error resigning: {}", e.getMessage());
      }
      try {
         _rtiAmbassador.disconnect();
      } catch (Exception e) {
         _logger.warn("Error disconnecting: {}", e.getMessage());
      }
   }

   // ==================== Proxy methods (called by gRPC service) ====================

   /** Publish the named attributes of an object class. Returns the class handle bytes. */
   public byte[] publishObjectAttributes(String className, List<Attribute_Values> attrValues)
         throws RTIexception
   {
      ObjectClassHandle classHandle = _rtiAmbassador.getObjectClassHandle(className);
      AttributeHandleSet attributes = CuisHandleFactory.attributeHandleSet();
      for (Attribute_Values av : attrValues) {
         attributes.add(_rtiAmbassador.getAttributeHandle(classHandle, av.getName()));
      }
      _rtiAmbassador.publishObjectClassAttributes(classHandle, attributes);
      return CuisHandleFactory.toBytes(classHandle);
   }

   /** Unpublish the named attributes, or the whole class if the list is empty. */
   public void unpublishObject(String className, List<Attributes> attrList)
         throws RTIexception
   {
      ObjectClassHandle classHandle = _rtiAmbassador.getObjectClassHandle(className);
      if (attrList.isEmpty()) {
         _rtiAmbassador.unpublishObjectClass(classHandle);
      } else {
         _rtiAmbassador.unpublishObjectClassAttributes(classHandle, attributeSet(classHandle, attrList));
      }
   }

   /** Subscribe to the named attributes of an object class. Returns the class handle bytes. */
   public byte[] subscribeObjectAttributes(String className, List<Attributes> attrList)
         throws RTIexception
   {
      ObjectClassHandle classHandle = _rtiAmbassador.getObjectClassHandle(className);
      _rtiAmbassador.subscribeObjectClassAttributes(classHandle, attributeSet(classHandle, attrList));
      return CuisHandleFactory.toBytes(classHandle);
   }

   /** Unsubscribe from the named attributes, or the whole class if the list is empty. */
   public void unsubscribeObject(String className, List<Attributes> attrList)
         throws RTIexception
   {
      ObjectClassHandle classHandle = _rtiAmbassador.getObjectClassHandle(className);
      if (attrList.isEmpty()) {
         _rtiAmbassador.unsubscribeObjectClass(classHandle);
      } else {
         _rtiAmbassador.unsubscribeObjectClassAttributes(classHandle, attributeSet(classHandle, attrList));
      }
   }

   public void updateAttributeValues(
         byte[] instanceHandleBytes,
         List<Attribute_Values> attrValues,
         byte[] userSuppliedTag)
         throws RTIexception
   {
      ObjectInstanceHandle instanceHandle = CuisHandleFactory.objectInstanceHandle(instanceHandleBytes);
      ObjectClassHandle classHandle = classForInstance(instanceHandleBytes);
      AttributeHandleValueMap values = CuisHandleFactory.attributeHandleValueMap(attrValues.size());
      for (Attribute_Values av : attrValues) {
         AttributeHandle handle = _rtiAmbassador.getAttributeHandle(classHandle, av.getName());
         values.put(handle, DataValueCodec.encode(av.getValue()));
      }
      _rtiAmbassador.updateAttributeValues(instanceHandle, values, userSuppliedTag);
   }

   /**
    * Request an attribute value update. The scope is either a specific
    * instance (instanceHandleBytes non-empty) or, by object class name,
    * all instances of that class.
    */
   public void requestObjectUpdate(
         String objectName,
         byte[] instanceHandleBytes,
         List<Attributes> attrList,
         byte[] userSuppliedTag)
         throws RTIexception
   {
      if (instanceHandleBytes != null && instanceHandleBytes.length > 0) {
         ObjectInstanceHandle instanceHandle = CuisHandleFactory.objectInstanceHandle(instanceHandleBytes);
         ObjectClassHandle classHandle = classForInstance(instanceHandleBytes);
         _rtiAmbassador.requestAttributeValueUpdate(
               instanceHandle, attributeSet(classHandle, attrList), userSuppliedTag);
      } else {
         ObjectClassHandle classHandle = _rtiAmbassador.getObjectClassHandle(objectName);
         _rtiAmbassador.requestAttributeValueUpdate(
               classHandle, attributeSet(classHandle, attrList), userSuppliedTag);
      }
   }

   public void publishInteraction(String interactionName)
         throws RTIexception
   {
      _rtiAmbassador.publishInteractionClass(_rtiAmbassador.getInteractionClassHandle(interactionName));
   }

   public void unpublishInteraction(String interactionName)
         throws RTIexception
   {
      _rtiAmbassador.unpublishInteractionClass(_rtiAmbassador.getInteractionClassHandle(interactionName));
   }

   public void subscribeInteraction(String interactionName)
         throws RTIexception
   {
      _rtiAmbassador.subscribeInteractionClass(_rtiAmbassador.getInteractionClassHandle(interactionName));
   }

   public void unsubscribeInteraction(String interactionName)
         throws RTIexception
   {
      _rtiAmbassador.unsubscribeInteractionClass(_rtiAmbassador.getInteractionClassHandle(interactionName));
   }

   public void sendInteraction(
         String interactionName,
         List<Parameter_Values> paramValues,
         byte[] userSuppliedTag)
         throws RTIexception
   {
      InteractionClassHandle interactionHandle = _rtiAmbassador.getInteractionClassHandle(interactionName);
      ParameterHandleValueMap values = CuisHandleFactory.parameterHandleValueMap(paramValues.size());
      for (Parameter_Values pv : paramValues) {
         ParameterHandle handle = _rtiAmbassador.getParameterHandle(interactionHandle, pv.getName());
         values.put(handle, DataValueCodec.encode(pv.getValue()));
      }
      _rtiAmbassador.sendInteraction(interactionHandle, values, userSuppliedTag);
   }

   // ==================== FederateAmbassador callbacks ====================

   @Override
   public void discoverObjectInstance(
         ObjectInstanceHandle objectInstance,
         ObjectClassHandle objectClass,
         String objectInstanceName,
         FederateHandle producingFederate)
         throws FederateInternalError
   {
      ByteString instanceBytes = ByteString.copyFrom(CuisHandleFactory.toBytes(objectInstance));
      ByteString classBytes = ByteString.copyFrom(CuisHandleFactory.toBytes(objectClass));
      _instanceToClassMap.put(instanceBytes, classBytes);

      String className = objectClassName(objectClass);
      DiscoverInstanceRequest request = DiscoverInstanceRequest.newBuilder()
            .setInstanceHandle(instanceBytes)
            .setObjectHandle(classBytes)
            .setObjectName(objectInstanceName)
            .build();
      _routingEngine.onDiscoverObjectInstance(className, request);
   }

   @Override
   public void reflectAttributeValues(
         ObjectInstanceHandle objectInstance,
         AttributeHandleValueMap attributeValues,
         byte[] userSuppliedTag,
         TransportationTypeHandle transportationType,
         FederateHandle producingFederate,
         RegionHandleSet optionalSentRegions)
         throws FederateInternalError
   {
      ByteString instanceBytes = ByteString.copyFrom(CuisHandleFactory.toBytes(objectInstance));
      ByteString classBytes = _instanceToClassMap.get(instanceBytes);
      if (classBytes == null) {
         _logger.warn("Received reflectAttributeValues for unknown instance: {}", objectInstance);
         return;
      }

      ObjectClassHandle classHandle = CuisHandleFactory.objectClassHandle(classBytes.toByteArray());
      ReflectValuesRequest.Builder request = ReflectValuesRequest.newBuilder()
            .setInstanceHandle(instanceBytes)
            .setUserSuppliedTag(tag(userSuppliedTag));
      for (Map.Entry<AttributeHandle, byte[]> entry : attributeValues.entrySet()) {
         request.addAttrValList(Attribute_Values.newBuilder()
               .setName(attributeName(classHandle, entry.getKey()))
               .setValue(DataValueCodec.toOpaque(entry.getValue()))
               .build());
      }
      _routingEngine.onReflectAttributeValues(objectClassName(classHandle), request.build());
   }

   @Override
   public void removeObjectInstance(
         ObjectInstanceHandle objectInstance,
         byte[] userSuppliedTag,
         FederateHandle producingFederate)
         throws FederateInternalError
   {
      ByteString instanceBytes = ByteString.copyFrom(CuisHandleFactory.toBytes(objectInstance));
      ByteString classBytes = _instanceToClassMap.remove(instanceBytes);
      if (classBytes == null) {
         _logger.warn("Received removeObjectInstance for unknown instance: {}", objectInstance);
         return;
      }

      String className = objectClassName(CuisHandleFactory.objectClassHandle(classBytes.toByteArray()));
      RemoveInstanceRequest request = RemoveInstanceRequest.newBuilder()
            .setInstanceHandle(instanceBytes)
            .setUserSuppliedTag(tag(userSuppliedTag))
            .build();
      _routingEngine.onRemoveObjectInstance(className, request);
   }

   @Override
   public void provideAttributeValueUpdate(
         ObjectInstanceHandle objectInstance,
         AttributeHandleSet attributes,
         byte[] userSuppliedTag)
         throws FederateInternalError
   {
      ByteString instanceBytes = ByteString.copyFrom(CuisHandleFactory.toBytes(objectInstance));
      ByteString classBytes = _instanceToClassMap.get(instanceBytes);
      if (classBytes == null) {
         _logger.warn("Received provideAttributeValueUpdate for unknown instance: {}", objectInstance);
         return;
      }

      ObjectClassHandle classHandle = CuisHandleFactory.objectClassHandle(classBytes.toByteArray());
      ProvideAttributeValueUpdateRequest.Builder request = ProvideAttributeValueUpdateRequest.newBuilder()
            .setInstanceHandle(instanceBytes)
            .setUserSuppliedTag(tag(userSuppliedTag));
      for (AttributeHandle ah : attributes) {
         request.addAttrList(Attributes.newBuilder().setName(attributeName(classHandle, ah)).build());
      }
      _routingEngine.onProvideAttributeValueUpdate(objectClassName(classHandle), request.build());
   }

   @Override
   public void receiveInteraction(
         InteractionClassHandle interactionClass,
         ParameterHandleValueMap parameterValues,
         byte[] userSuppliedTag,
         TransportationTypeHandle transportationType,
         FederateHandle producingFederate,
         RegionHandleSet optionalSentRegions)
         throws FederateInternalError
   {
      String interactionName = interactionClassName(interactionClass);
      ReceiveInteractionRequest.Builder request = ReceiveInteractionRequest.newBuilder()
            .setName(interactionName)
            .setUserSuppliedTag(tag(userSuppliedTag));
      for (Map.Entry<ParameterHandle, byte[]> entry : parameterValues.entrySet()) {
         request.addParamValList(Parameter_Values.newBuilder()
               .setName(parameterName(interactionClass, entry.getKey()))
               .setValue(DataValueCodec.toOpaque(entry.getValue()))
               .build());
      }
      _routingEngine.onReceiveInteraction(interactionName, request.build());
   }

   /**
    * Register an object instance -> class mapping externally (e.g. when a sub-app registers).
    */
   public void trackInstance(ByteString instanceHandle, ByteString classHandle)
   {
      _instanceToClassMap.put(instanceHandle, classHandle);
   }

   // ==================== Helpers ====================

   private AttributeHandleSet attributeSet(ObjectClassHandle classHandle, List<Attributes> attrList)
         throws RTIexception
   {
      AttributeHandleSet attributes = CuisHandleFactory.attributeHandleSet();
      for (Attributes a : attrList) {
         attributes.add(_rtiAmbassador.getAttributeHandle(classHandle, a.getName()));
      }
      return attributes;
   }

   private ObjectClassHandle classForInstance(byte[] instanceHandleBytes)
   {
      ByteString classBytes = _instanceToClassMap.get(ByteString.copyFrom(instanceHandleBytes));
      if (classBytes == null) {
         throw new IllegalStateException("Unknown object instance; no class mapping is tracked for it");
      }
      return CuisHandleFactory.objectClassHandle(classBytes.toByteArray());
   }

   private String objectClassName(ObjectClassHandle classHandle)
   {
      try {
         return _rtiAmbassador.getObjectClassName(classHandle);
      } catch (RTIexception e) {
         _logger.warn("Could not resolve object class name: {}", e.getMessage());
         return classHandle.toString();
      }
   }

   private String attributeName(ObjectClassHandle classHandle, AttributeHandle attributeHandle)
   {
      try {
         return _rtiAmbassador.getAttributeName(classHandle, attributeHandle);
      } catch (RTIexception e) {
         _logger.warn("Could not resolve attribute name: {}", e.getMessage());
         return attributeHandle.toString();
      }
   }

   private String interactionClassName(InteractionClassHandle interactionHandle)
   {
      try {
         return _rtiAmbassador.getInteractionClassName(interactionHandle);
      } catch (RTIexception e) {
         _logger.warn("Could not resolve interaction class name: {}", e.getMessage());
         return interactionHandle.toString();
      }
   }

   private String parameterName(InteractionClassHandle interactionHandle, ParameterHandle parameterHandle)
   {
      try {
         return _rtiAmbassador.getParameterName(interactionHandle, parameterHandle);
      } catch (RTIexception e) {
         _logger.warn("Could not resolve parameter name: {}", e.getMessage());
         return parameterHandle.toString();
      }
   }

   private static ByteString tag(byte[] userSuppliedTag)
   {
      return userSuppliedTag != null ? ByteString.copyFrom(userSuppliedTag) : ByteString.EMPTY;
   }
}
