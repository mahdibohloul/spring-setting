package io.github.mahdibohloul.spring.setting.admin.web.features

/**
 * Discovers all [AdminFeatureDescriptor] beans known to the application and exposes them
 * to the REST surface. Filtering by caller roles happens at lookup time so the SPA only
 * sees features the user is allowed to interact with.
 */
class AdminFeatureRegistry(
  descriptors: List<AdminFeatureDescriptor>,
) {
  private val byId: Map<String, AdminFeatureDescriptor> = descriptors.associateBy { it.id }

  fun all(): Collection<AdminFeatureDescriptor> = byId.values

  /**
   * Returns features visible to a caller holding [grantedRoles]. A feature is visible when
   * its [AdminFeatureDescriptor.visibleToRoles] is empty (anyone) or intersects the caller's roles.
   */
  fun visibleTo(grantedRoles: Set<String>): List<AdminFeatureDescriptor> = byId.values.filter {
    it.visibleToRoles.isEmpty() || it.visibleToRoles.intersect(grantedRoles).isNotEmpty()
  }

  fun get(id: String): AdminFeatureDescriptor? = byId[id]
}
