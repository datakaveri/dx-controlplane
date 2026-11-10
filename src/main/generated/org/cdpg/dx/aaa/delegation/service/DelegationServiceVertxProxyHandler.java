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

import org.cdpg.dx.aaa.delegation.service.DelegationService;
import io.vertx.core.Vertx;
import io.vertx.core.Handler;
import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import io.vertx.serviceproxy.ProxyHandler;
import io.vertx.serviceproxy.ServiceException;
import io.vertx.serviceproxy.ServiceExceptionMessageCodec;
import io.vertx.serviceproxy.HelperUtils;
import io.vertx.serviceproxy.ServiceBinder;

import java.util.List;
import java.util.Set;
import io.vertx.core.Future;
/*
  Generated Proxy code - DO NOT EDIT
  @author Roger the Robot
*/

@SuppressWarnings({"unchecked", "rawtypes"})
public class DelegationServiceVertxProxyHandler extends ProxyHandler {

  public static final long DEFAULT_CONNECTION_TIMEOUT = 5 * 60; // 5 minutes 
  private final Vertx vertx;
  private final DelegationService service;
  private final long timerID;
  private long lastAccessed;
  private final long timeoutSeconds;
  private final boolean includeDebugInfo;

  public DelegationServiceVertxProxyHandler(Vertx vertx, DelegationService service){
    this(vertx, service, DEFAULT_CONNECTION_TIMEOUT);
  }

  public DelegationServiceVertxProxyHandler(Vertx vertx, DelegationService service, long timeoutInSecond){
    this(vertx, service, true, timeoutInSecond);
  }

  public DelegationServiceVertxProxyHandler(Vertx vertx, DelegationService service, boolean topLevel, long timeoutInSecond){
    this(vertx, service, true, timeoutInSecond, false);
  }

  public DelegationServiceVertxProxyHandler(Vertx vertx, DelegationService service, boolean topLevel, long timeoutSeconds, boolean includeDebugInfo) {
      this.vertx = vertx;
      this.service = service;
      this.includeDebugInfo = includeDebugInfo;
      this.timeoutSeconds = timeoutSeconds;
      try {
        this.vertx.eventBus().registerDefaultCodec(ServiceException.class,
            new ServiceExceptionMessageCodec());
      } catch (IllegalStateException ex) {}
      if (timeoutSeconds != -1 && !topLevel) {
        long period = timeoutSeconds * 1000 / 2;
        if (period > 10000) {
          period = 10000;
        }
        this.timerID = vertx.setPeriodic(period, this::checkTimedOut);
      } else {
        this.timerID = -1;
      }
      accessed();
    }


  private void checkTimedOut(long id) {
    long now = System.nanoTime();
    if (now - lastAccessed > timeoutSeconds * 1000000000) {
      close();
    }
  }

    @Override
    public void close() {
      if (timerID != -1) {
        vertx.cancelTimer(timerID);
      }
      super.close();
    }

    private void accessed() {
      this.lastAccessed = System.nanoTime();
    }

  public void handle(Message<JsonObject> msg) {
    try{
      JsonObject json = msg.body();
      String action = msg.headers().get("action");
      if (action == null) throw new IllegalStateException("action not specified");
      accessed();
      switch (action) {
        case "createDelegationGrant": {
          service.createDelegationGrant((io.vertx.core.json.JsonObject)json.getValue("delegationGrant"),
                        HelperUtils.convertSet(json.getJsonArray("UserRoles").getList()),
                        (io.vertx.core.json.JsonArray)json.getValue("roleConstraints")).onComplete(HelperUtils.createHandler(msg, includeDebugInfo));
          break;
        }
        case "getDelegationGrantById": {
          service.getDelegationGrantById((java.lang.String)json.getValue("delegationId")).onComplete(HelperUtils.createHandler(msg, includeDebugInfo));
          break;
        }
        case "getDelegationScopeByEntityId": {
          service.getDelegationScopeByEntityId((java.lang.String)json.getValue("entity")).onComplete(HelperUtils.createListHandler(msg, includeDebugInfo));
          break;
        }
        case "getAllDelegationsOfDelegate": {
          service.getAllDelegationsOfDelegate((java.lang.String)json.getValue("userId")).onComplete(HelperUtils.createListHandler(msg, includeDebugInfo));
          break;
        }
        case "getAllDelegationsByDelegator": {
          service.getAllDelegationsByDelegator((java.lang.String)json.getValue("userId")).onComplete(HelperUtils.createListHandler(msg, includeDebugInfo));
          break;
        }
        case "deleteDelegation": {
          service.deleteDelegation((java.lang.String)json.getValue("delegationId"),
                        (java.lang.String)json.getValue("userId")).onComplete(HelperUtils.createHandler(msg, includeDebugInfo));
          break;
        }
        case "getDelegationRequestsByDelegationId": {
          service.getDelegationRequestsByDelegationId((java.lang.String)json.getValue("delegationId")).onComplete(HelperUtils.createListHandler(msg, includeDebugInfo));
          break;
        }
        case "getDelegationScopeConstraints": {
          service.getDelegationScopeConstraints((java.lang.String)json.getValue("delegationId")).onComplete(HelperUtils.createListHandler(msg, includeDebugInfo));
          break;
        }
        case "getAllDelegationScopeConstraints": {
          service.getAllDelegationScopeConstraints((java.lang.String)json.getValue("itemId")).onComplete(HelperUtils.createListHandler(msg, includeDebugInfo));
          break;
        }
        case "checkItemAccess": {
          service.checkItemAccess((java.lang.String)json.getValue("delegatorId"),
                        (java.lang.String)json.getValue("delegateId")).onComplete(HelperUtils.createHandler(msg, includeDebugInfo));
          break;
        }
        default: throw new IllegalStateException("Invalid action: " + action);
      }
    } catch (Throwable t) {
      if (includeDebugInfo) msg.reply(new ServiceException(500, t.getMessage(), HelperUtils.generateDebugInfo(t)));
      else msg.reply(new ServiceException(500, t.getMessage()));
      throw t;
    }
  }
}