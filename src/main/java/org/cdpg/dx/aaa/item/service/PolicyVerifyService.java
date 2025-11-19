package org.cdpg.dx.aaa.item.service;

import io.vertx.core.Future;
import org.cdpg.dx.acl.policy.dao.model.VerifyPolicyDto;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.common.model.DxUser;

public interface PolicyVerifyService {
  /**
   * Verifies policy for a given requester and owner.
   * Internally decides between default/internal APD or external APD.
   */
  Future<VerifyPolicyDto> verify(String apdUrl, DxUser requester, DxUser owner,
                                 String itemId, ItemType itemType, String token);
}


