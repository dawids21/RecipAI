package xyz.stasiak.recipai.permissions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ResourceInviteRepository extends JpaRepository<ResourceInvite, UUID> {

    boolean existsByResourceTypeAndResourceIdAndEmail(String resourceType, UUID resourceId, String email);

    List<ResourceInvite> findByEmailOrderByCreatedAtDesc(String email);

    List<ResourceInvite> findByResourceTypeAndResourceIdOrderByCreatedAtAsc(String resourceType, UUID resourceId);

    void deleteByResourceTypeAndResourceIdAndEmail(String resourceType, UUID resourceId, String email);

    void deleteByResourceTypeAndResourceId(String resourceType, UUID resourceId);
}
