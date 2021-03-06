/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.accounts.api.internal.impl;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.security.PermitAll;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.hawkular.accounts.api.NamedRole;
import org.hawkular.accounts.api.OrganizationMembershipService;
import org.hawkular.accounts.api.OrganizationService;
import org.hawkular.accounts.api.internal.adapter.HawkularAccounts;
import org.hawkular.accounts.api.model.Organization;
import org.hawkular.accounts.api.model.OrganizationMembership;
import org.hawkular.accounts.api.model.Organization_;
import org.hawkular.accounts.api.model.Persona;
import org.hawkular.accounts.api.model.Role;

/**
 * @author Juraci Paixão Kröhling
 */
@Stateless
@PermitAll
public class OrganizationServiceImpl implements OrganizationService {
    @Inject
    @HawkularAccounts
    EntityManager em;

    @Inject
    OrganizationMembershipService membershipService;

    @Inject
    @NamedRole("SuperUser")
    Role superUser;

    @Override
    public List<Organization> getOrganizationsForPersona(Persona persona) {
        return getOrganizationsFromMemberships(membershipService.getMembershipsForPersona(persona));
    }

    @Override
    public List<Organization> getOrganizationsFromMemberships(List<OrganizationMembership> memberships) {
        return memberships
                .stream()
                .map(OrganizationMembership::getOrganization)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public Organization createOrganization(String name, String description, Persona owner) {
        Organization organization = new Organization(owner);
        organization.setName(name);
        organization.setDescription(description);
        em.persist(organization);
        em.persist(new OrganizationMembership(organization, owner, superUser));
        return organization;
    }

    @Override
    public void deleteOrganization(Organization organization) {
        membershipService.getMembershipsForOrganization(organization).stream().forEach(em::remove);
        em.remove(organization);
    }

    @Override
    public Organization get(String id) {
        if (null == id) {
            throw new IllegalArgumentException("The given resource ID is invalid (null).");
        }

        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<Organization> query = builder.createQuery(Organization.class);
        Root<Organization> root = query.from(Organization.class);
        query.select(root);
        query.where(builder.equal(root.get(Organization_.id), id));

        List<Organization> results = em.createQuery(query).getResultList();
        if (results.size() == 1) {
            return results.get(0);
        }

        if (results.size() > 1) {
            throw new IllegalStateException("More than one organization found for ID " + id);
        }

        return null;
    }
}
