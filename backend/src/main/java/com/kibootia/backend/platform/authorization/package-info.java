/**
 * The authorization contract every module uses (ADR 0027, ADR 0044): permissions, the decision point port, the
 * {@code Authorization} entry point and the {@code @RequiresPermission} and {@code @NoAuthorization} markers. The
 * decision point itself is implemented by the {@code access} module. The contract lives here so that any module can
 * protect a use case without depending on {@code access}, which would otherwise create module cycles.
 */
@NamedInterface("authorization")
package com.kibootia.backend.platform.authorization;

import org.springframework.modulith.NamedInterface;
