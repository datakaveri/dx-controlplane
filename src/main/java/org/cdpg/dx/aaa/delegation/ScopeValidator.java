//package org.cdpg.dx.aaa.delegation;
//
//import io.vertx.core.Future;
//import org.cdpg.dx.aaa.asset.service.AssetService;
//import org.cdpg.dx.aaa.delegation.util.ScopeType;
//import org.cdpg.dx.aaa.organization.service.OrganizationService;
//import org.cdpg.dx.common.exception.DxBadRequestException;
//
//import java.util.List;
//import java.util.Set;
//import java.util.UUID;
//
//public class ScopeValidator {
//
//  private final OrganizationService organizationService;
//  private final AssetService assetService;
//
//  public ScopeValidator(OrganizationService organizationService, AssetService assetService) {
//    this.organizationService = organizationService;
//    this.assetService = assetService;
//  }
//
//  public Future<Void> validateScopePermissions(
//    ScopeType scopeType,
//    UUID delegatorId,
//    UUID delegateId,
//    List<String> entityIds,
//    Set<String> userRoles) {
//
//    return switch (scopeType) {
//      case COS_ADMIN -> validateCosAdminScope(userRoles);
//      case ORG_MANAGEMENT -> validateOrgManagementScope(delegatorId, delegateId, entityIds, userRoles);
//      case ASSET_MANAGEMENT -> validateAssetManagementScope(delegatorId, delegateId, entityIds, userRoles);
//      case COMPUTE_MANAGEMENT -> validateComputeManagementScope(delegatorId, userRoles);
//      case CREDIT_MANAGEMENT -> validateCreditsManagementScope(delegatorId, userRoles);
//      case PROVIDER_MANAGEMENT -> validateProviderManagementScope(delegatorId, delegateId, userRoles);
//      default -> Future.failedFuture(new DxBadRequestException("Unsupported scope type: " + scopeType));
//    };
//  }
//
//  /* ------------ INDIVIDUAL VALIDATION METHODS ------------ */
//
//  private Future<Void> validateCosAdminScope(Set<String> userRoles) {
//    if (!userRoles.contains("cos_admin")) {
//      return Future.failedFuture(new DxBadRequestException("Delegator must have cos_admin role"));
//    }
//    return Future.succeededFuture();
//  }
//
//  private Future<Void> validateOrgManagementScope(UUID delegatorId, UUID delegateId, List<String> orgIds, Set<String> userRoles) {
//    if (!userRoles.contains("org_admin")) {
//      return Future.failedFuture(new DxBadRequestException("Delegator must have org_admin role"));
//    }
//
//    // Validate org relation
//    return organizationService.getOrganizationUserInfo(delegatorId)
//      .compose(delegatorOrg -> {
//        UUID delegatorOrgId = delegatorOrg.organizationId()();
//
//        if (!orgIds.contains(delegatorOrgId.toString())) {
//          return Future.failedFuture(new DxBadRequestException("Invalid organization ID for org_management"));
//        }
//
//        return organizationService.getOrganizationUserInfo(delegateId)
//          .compose(delegateOrg -> {
//            if (!delegateOrg.organizationId().equals(delegatorOrgId)) {
//              return Future.failedFuture(new DxBadRequestException("Delegate must be in the same organization"));
//            }
//            return Future.succeededFuture();
//          });
//      });
//  }
//
//  private Future<Void> validateAssetManagementScope(UUID delegatorId, UUID delegateId, List<String> assetIds, Set<String> userRoles) {
//    if (userRoles.stream().noneMatch(r -> List.of("provider", "org_admin", "cos_admin").contains(r))) {
//      return Future.failedFuture(new DxBadRequestException("Delegator lacks permission for asset_management"));
//    }
//
//    return organizationUserService.getOrganizationUserInfo(delegatorId)
//      .compose(delegatorOrg -> organizationUserService.getOrganizationUserInfo(delegateId)
//        .compose(delegateOrg -> {
//          if (!delegateOrg.getOrganizationId().equals(delegatorOrg.getOrganizationId())) {
//            return Future.failedFuture(new DxBadRequestException("Delegate not in same organization"));
//          }
//
//          // For each asset, check ownership or org-level linkage
//          List<Future> checks = assetIds.stream().map(assetId ->
//            assetService.verifyAssetOwnership(UUID.fromString(assetId), delegatorId, userRoles)
//          ).toList();
//
//          return CompositeFuture.all(checks).mapEmpty();
//        }));
//  }
////
//  private Future<Void> validateComputeManagementScope(UUID delegatorId, Set<String> userRoles) {
//    if (!userRoles.contains("cos_admin")) {
//      return Future.failedFuture(new DxBadRequestException("Only cos_admin can delegate compute_management"));
//    }
//    return Future.succeededFuture();
//  }
//
//  private Future<Void> validateCreditsManagementScope(UUID delegatorId, Set<String> userRoles) {
//    if (!userRoles.contains("cos_admin")) {
//      return Future.failedFuture(new DxBadRequestException("Only cos_admin can delegate credits_management"));
//    }
//    return Future.succeededFuture();
//  }
//
//  private Future<Void> validateProviderManagementScope(UUID delegatorId, UUID delegateId, Set<String> userRoles) {
//    if (userRoles.stream().noneMatch(r -> List.of("org_admin", "cos_admin").contains(r))) {
//      return Future.failedFuture(new DxBadRequestException("Delegator must have org_admin or cos_admin role"));
//    }
//
//    if (userRoles.contains("cos_admin")) {
//      return Future.succeededFuture();
//    }
//
//    // org_admin check for same organization
//    return organizationUserService.getOrganizationUserInfo(delegatorId)
//      .compose(delegatorOrg -> organizationUserService.getOrganizationUserInfo(delegateId)
//        .compose(delegateOrg -> {
//          if (!delegateOrg.getOrganizationId().equals(delegatorOrg.getOrganizationId())) {
//            return Future.failedFuture(new DxBadRequestException("Delegate not in same organization"));
//          }
//          return Future.succeededFuture();
//        }));
//  }
//}
