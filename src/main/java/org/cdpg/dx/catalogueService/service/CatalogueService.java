package org.cdpg.dx.catalogueService.service;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.cdpg.dx.catalogueService.models.Asset;
import org.cdpg.dx.catalogueService.models.ResourceObj;

/**
 * Interface for the Catalogue Client, responsible for fetching resource/resource_group from the
 * Catalogue server and updating the resource_entity table in the database.
 */
@VertxGen
@ProxyGen
public interface CatalogueService {
  @GenIgnore
  static CatalogueService createProxy(Vertx vertx, String address) {
    return new CatalogueServiceVertxEBProxy(vertx, address);
  }

  /**
   * Fetches resource/resource_group from the Catalogue server and updates the resource_entity table in the
   * database.
   *
   * @param id A set of unique IDs of resource/resource_group to be fetched.
   * @return A Future containing a list of ResourceObj representing the fetched resources. The
   * Future is resolved with the fetched resourceObj list on success, or failed with an error
   * message on failure.
   */
  Future<ResourceObj> fetchItems(String id);

  Future<Asset> fetchAsset(String itemId);
}