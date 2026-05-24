package io.github.mahdibohloul.spring.setting.admin.web.features

/**
 * Declarative descriptor for a UI-rendered admin feature.
 *
 * The admin UI panel does not hard-code any feature — it fetches `GET /features` at boot,
 * receives the JSON spec for every [AdminFeatureDescriptor] bean visible to the caller, and
 * renders sidebar entries + operation forms dynamically.
 *
 * The webflux module ships [SettingsAdminFeature] which advertises the built-in
 * setting CRUD. Consumer services add new features (e.g. an order-patch editor) by
 * registering their own `@Component AdminFeatureDescriptor` — no UI code change needed.
 *
 * Operation paths are templates (`/orders/{orderId}/patch`); the SPA fills placeholders
 * with the user's input.
 */
interface AdminFeatureDescriptor {
  /** Stable identifier shown in URLs (e.g. `"settings"`, `"orders.patch"`). */
  val id: String

  /** Human-readable label rendered in the sidebar. */
  val label: String

  /** Route prefix the SPA mounts the feature under (e.g. `"/settings"`). */
  val routePrefix: String

  /** Roles required to even see this feature in the sidebar. */
  val visibleToRoles: Set<String>

  /** Operations this feature exposes. The UI renders a form per operation. */
  fun operations(): List<AdminOperation>
}

/**
 * One callable action exposed by an [AdminFeatureDescriptor].
 *
 * The sealed hierarchy lets the UI render a form-shape matching the semantic intent of
 * the operation — JSON merge patch shows a diff-friendly editor, a full replace shows a
 * raw editor with a warning, an Action shows a confirmation modal, etc.
 */
sealed interface AdminOperation {
  val id: String
  val label: String
  val httpMethod: String
  val pathTemplate: String
  val requiredRoles: Set<String>

  data class JsonMergePatch(
    override val id: String,
    override val label: String,
    override val pathTemplate: String,
    override val requiredRoles: Set<String>,
  ) : AdminOperation {
    override val httpMethod: String = "PATCH"
  }

  data class Replace(
    override val id: String,
    override val label: String,
    override val pathTemplate: String,
    override val requiredRoles: Set<String>,
  ) : AdminOperation {
    override val httpMethod: String = "PUT"
  }

  data class Delete(
    override val id: String,
    override val label: String,
    override val pathTemplate: String,
    override val requiredRoles: Set<String>,
  ) : AdminOperation {
    override val httpMethod: String = "DELETE"
  }

  data class Action(
    override val id: String,
    override val label: String,
    override val httpMethod: String,
    override val pathTemplate: String,
    override val requiredRoles: Set<String>,
  ) : AdminOperation
}
