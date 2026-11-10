package org.cdpg.dx.aaa.central.catalogue.search.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.central.catalogue.search.util.ResponseModel;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;

public interface CentralSearchService {

  Future<ResponseModel> postSearch(QueryDecoderRequestDTO queryDecoder);

  Future<ResponseModel> postCount(QueryDecoderRequestDTO queryDecoder);
}
