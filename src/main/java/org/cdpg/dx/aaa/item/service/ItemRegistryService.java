package org.cdpg.dx.aaa.item.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.item.model.Item;
import org.cdpg.dx.aaa.item.model.DataBankCreationResponse;
import org.cdpg.dx.aaa.item.util.DataBankCreationRequest;

public interface ItemRegistryService {
  Future<DataBankCreationResponse> createDataBankWithIntegrations(DataBankCreationRequest dataBankCreationRequest, Item item);
}


