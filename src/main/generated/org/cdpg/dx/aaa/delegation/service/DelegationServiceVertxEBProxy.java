/*
* Copyright 2014 Red Hat, Inc.
*
* Red Hat licenses this file to you under the Apache License, version 2.0
* (the "License"); you may not use this file except in compliance with the
* License. You may obtain a copy of the License at:
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations
* under the License.
*/

package org.cdpg.dx.aaa.delegation.service;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;
import io.vertx.serviceproxy.ServiceException;
import io.vertx.serviceproxy.ServiceExceptionMessageCodec;
import io.vertx.serviceproxy.ProxyUtils;

import java.util.List;
import java.util.Set;
import io.vertx.core.Future;
/*
  Generated Proxy code - DO NOT EDIT
  @author Roger the Robot
*/

@SuppressWarnings({"unchecked", "rawtypes"})
public class DelegationServiceVertxEBProxy implements DelegationService {
  private Vertx _vertx;
  private String _address;
  private DeliveryOptions _options;
  private boolean closed;

  public DelegationServiceVertxEBProxy(Vertx vertx, String address) {
    this(vertx, address, null);
  }

  public DelegationServiceVertxEBProxy(Vertx vertx, String address, DeliveryOptions options) {
    this._vertx = vertx;
    this._address = address;
    this._options = options;
    try {
      this._vertx.eventBus().registerDefaultCodec(ServiceException.class, new ServiceExceptionMessageCodec());
    } catch (IllegalStateException ex) {
    }
  }

  @Override
  public Future<JsonObject> createDelegationGrant(JsonObject delegationGrant, Set<String> UserRoles, JsonArray roleConstraints){
    if (closed) return io.vertx.core.Future.failedFuture("Proxy is closed");
    JsonObject _json = new JsonObject();
    _json.put("delegationGrant", delegationGrant);
    _json.put("UserRoles", new JsonArray(new ArrayList<>(UserRoles)));
    _json.put("roleConstraints", roleConstraints);

    DeliveryOptions _deliveryOptions = (_options != null) ? new DeliveryOptions(_options) : new DeliveryOptions();
    _deliveryOptions.addHeader("action", "createDelegationGrant");
    _deliveryOptions.getHeaders().set("action", "createDelegationGrant");
    return _vertx.eventBus().<JsonObject>request(_address, _json, _deliveryOptions).map(msg -> {
      return msg.body();
    });
  }
  @Override
  public Future<JsonObject> getDelegationGrantById(String delegationId){
    if (closed) return io.vertx.core.Future.failedFuture("Proxy is closed");
    JsonObject _json = new JsonObject();
    _json.put("delegationId", delegationId);

    DeliveryOptions _deliveryOptions = (_options != null) ? new DeliveryOptions(_options) : new DeliveryOptions();
    _deliveryOptions.addHeader("action", "getDelegationGrantById");
    _deliveryOptions.getHeaders().set("action", "getDelegationGrantById");
    return _vertx.eventBus().<JsonObject>request(_address, _json, _deliveryOptions).map(msg -> {
      return msg.body();
    });
  }
  @Override
  public Future<List<JsonObject>> getDelegationScopeByEntityId(String entity){
    if (closed) return io.vertx.core.Future.failedFuture("Proxy is closed");
    JsonObject _json = new JsonObject();
    _json.put("entity", entity);

    DeliveryOptions _deliveryOptions = (_options != null) ? new DeliveryOptions(_options) : new DeliveryOptions();
    _deliveryOptions.addHeader("action", "getDelegationScopeByEntityId");
    _deliveryOptions.getHeaders().set("action", "getDelegationScopeByEntityId");
    return _vertx.eventBus().<JsonArray>request(_address, _json, _deliveryOptions).map(msg -> {
      return ProxyUtils.convertList(msg.body().getList());
    });
  }
  @Override
  public Future<List<JsonObject>> getAllDelegationsOfDelegate(String userId){
    if (closed) return io.vertx.core.Future.failedFuture("Proxy is closed");
    JsonObject _json = new JsonObject();
    _json.put("userId", userId);

    DeliveryOptions _deliveryOptions = (_options != null) ? new DeliveryOptions(_options) : new DeliveryOptions();
    _deliveryOptions.addHeader("action", "getAllDelegationsOfDelegate");
    _deliveryOptions.getHeaders().set("action", "getAllDelegationsOfDelegate");
    return _vertx.eventBus().<JsonArray>request(_address, _json, _deliveryOptions).map(msg -> {
      return ProxyUtils.convertList(msg.body().getList());
    });
  }
  @Override
  public Future<List<JsonObject>> getAllDelegationsByDelegator(String userId){
    if (closed) return io.vertx.core.Future.failedFuture("Proxy is closed");
    JsonObject _json = new JsonObject();
    _json.put("userId", userId);

    DeliveryOptions _deliveryOptions = (_options != null) ? new DeliveryOptions(_options) : new DeliveryOptions();
    _deliveryOptions.addHeader("action", "getAllDelegationsByDelegator");
    _deliveryOptions.getHeaders().set("action", "getAllDelegationsByDelegator");
    return _vertx.eventBus().<JsonArray>request(_address, _json, _deliveryOptions).map(msg -> {
      return ProxyUtils.convertList(msg.body().getList());
    });
  }
  @Override
  public Future<Boolean> deleteDelegation(String delegationId, String userId){
    if (closed) return io.vertx.core.Future.failedFuture("Proxy is closed");
    JsonObject _json = new JsonObject();
    _json.put("delegationId", delegationId);
    _json.put("userId", userId);

    DeliveryOptions _deliveryOptions = (_options != null) ? new DeliveryOptions(_options) : new DeliveryOptions();
    _deliveryOptions.addHeader("action", "deleteDelegation");
    _deliveryOptions.getHeaders().set("action", "deleteDelegation");
    return _vertx.eventBus().<Boolean>request(_address, _json, _deliveryOptions).map(msg -> {
      return msg.body();
    });
  }
  @Override
  public Future<List<JsonObject>> getDelegationRequestsByDelegationId(String delegationId){
    if (closed) return io.vertx.core.Future.failedFuture("Proxy is closed");
    JsonObject _json = new JsonObject();
    _json.put("delegationId", delegationId);

    DeliveryOptions _deliveryOptions = (_options != null) ? new DeliveryOptions(_options) : new DeliveryOptions();
    _deliveryOptions.addHeader("action", "getDelegationRequestsByDelegationId");
    _deliveryOptions.getHeaders().set("action", "getDelegationRequestsByDelegationId");
    return _vertx.eventBus().<JsonArray>request(_address, _json, _deliveryOptions).map(msg -> {
      return ProxyUtils.convertList(msg.body().getList());
    });
  }
  @Override
  public Future<List<JsonObject>> getDelegationScopeConstraints(String delegationId){
    if (closed) return io.vertx.core.Future.failedFuture("Proxy is closed");
    JsonObject _json = new JsonObject();
    _json.put("delegationId", delegationId);

    DeliveryOptions _deliveryOptions = (_options != null) ? new DeliveryOptions(_options) : new DeliveryOptions();
    _deliveryOptions.addHeader("action", "getDelegationScopeConstraints");
    _deliveryOptions.getHeaders().set("action", "getDelegationScopeConstraints");
    return _vertx.eventBus().<JsonArray>request(_address, _json, _deliveryOptions).map(msg -> {
      return ProxyUtils.convertList(msg.body().getList());
    });
  }
  @Override
  public Future<List<JsonObject>> getAllDelegationScopeConstraints(String itemId){
    if (closed) return io.vertx.core.Future.failedFuture("Proxy is closed");
    JsonObject _json = new JsonObject();
    _json.put("itemId", itemId);

    DeliveryOptions _deliveryOptions = (_options != null) ? new DeliveryOptions(_options) : new DeliveryOptions();
    _deliveryOptions.addHeader("action", "getAllDelegationScopeConstraints");
    _deliveryOptions.getHeaders().set("action", "getAllDelegationScopeConstraints");
    return _vertx.eventBus().<JsonArray>request(_address, _json, _deliveryOptions).map(msg -> {
      return ProxyUtils.convertList(msg.body().getList());
    });
  }
  @Override
  public Future<JsonObject> checkItemAccess(String delegatorId, String delegateId){
    if (closed) return io.vertx.core.Future.failedFuture("Proxy is closed");
    JsonObject _json = new JsonObject();
    _json.put("delegatorId", delegatorId);
    _json.put("delegateId", delegateId);

    DeliveryOptions _deliveryOptions = (_options != null) ? new DeliveryOptions(_options) : new DeliveryOptions();
    _deliveryOptions.addHeader("action", "checkItemAccess");
    _deliveryOptions.getHeaders().set("action", "checkItemAccess");
    return _vertx.eventBus().<JsonObject>request(_address, _json, _deliveryOptions).map(msg -> {
      return msg.body();
    });
  }
}
