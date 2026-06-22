package com.postwerk.service;

import com.postwerk.dto.admin.StaffCandidateResponse;
import com.postwerk.dto.admin.StaffKpisResponse;
import com.postwerk.dto.admin.StaffMemberResponse;
import com.postwerk.dto.admin.StaffRolesMatrixResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Platform-staff Staff &amp; Roles console: the staff roster, role KPIs, the read-only role→capability
 * matrix, grant-access candidates, and grant / change-role / revoke mutations. View + mutate gate on
 * {@code STAFF_MANAGE} (Super Admin only); self-change is rejected; every mutation is audit-logged.
 *
 * @since 1.0
 */
public interface AdminStaffService {

    Page<StaffMemberResponse> listRoster(String search, String role, String tier, String sort,
                                         String actorEmail, Pageable pageable);

    StaffKpisResponse kpis();

    StaffRolesMatrixResponse rolesMatrix();

    List<StaffCandidateResponse> candidates(String search);

    /** Grant (old role null) or change a staff role. */
    StaffMemberResponse setRole(UUID userId, String role, String actorEmail, String ip);

    /** Revoke all staff access (clears the role). */
    StaffMemberResponse revoke(UUID userId, String actorEmail, String ip);
}
