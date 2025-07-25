package org.cdpg.dx.aaa.list.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;

public interface ListService {
  Future<ResponseModel> getAvailableFilters(QueryDecoderRequestDTO queryDecoderRequestDTO);
}
