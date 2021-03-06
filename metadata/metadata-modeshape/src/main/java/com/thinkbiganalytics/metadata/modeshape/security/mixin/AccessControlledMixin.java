/**
 * 
 */
package com.thinkbiganalytics.metadata.modeshape.security.mixin;

import java.util.Collections;

/*-
 * #%L
 * kylo-metadata-modeshape
 * %%
 * Copyright (C) 2017 ThinkBig Analytics
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.security.Privilege;

import com.thinkbiganalytics.metadata.api.security.AccessControlled;
import com.thinkbiganalytics.metadata.api.security.RoleMembership;
import com.thinkbiganalytics.metadata.modeshape.common.mixin.NodeEntityMixin;
import com.thinkbiganalytics.metadata.modeshape.security.action.JcrAllowedActions;
import com.thinkbiganalytics.metadata.modeshape.security.role.JcrRoleMembership;
import com.thinkbiganalytics.metadata.modeshape.security.role.JcrSecurityRole;
import com.thinkbiganalytics.metadata.modeshape.support.JcrUtil;
import com.thinkbiganalytics.security.UsernamePrincipal;
import com.thinkbiganalytics.security.action.AllowedActions;
import com.thinkbiganalytics.security.role.SecurityRole;

/**
 *
 */
public interface AccessControlledMixin extends AccessControlled, NodeEntityMixin {
    
    @Override
    default Set<RoleMembership> getRoleMemberships() {
        JcrAllowedActions allowed = getJcrAllowedActions();
        
        return JcrUtil.getPropertyObjectSet(getNode(), JcrRoleMembership.NODE_NAME, JcrRoleMembership.class, allowed).stream()
                      .map(RoleMembership.class::cast)
                      .collect(Collectors.toSet());
    }
    
    @Override
    default Optional<RoleMembership> getRoleMembership(String roleName) {
        JcrAllowedActions allowed = getJcrAllowedActions();
        
        return JcrRoleMembership.find(getNode(), roleName, allowed).map(RoleMembership.class::cast);
    }
    
    @Override
    default AllowedActions getAllowedActions() {
        return getJcrAllowedActions();
    }

    default JcrAllowedActions getJcrAllowedActions() {
        Node allowedNode = JcrUtil.getNode(getNode(), JcrAllowedActions.NODE_NAME);
        return JcrUtil.createJcrObject(allowedNode, getJcrAllowedActionsType());
    }
    
    default void disableAccessControl(JcrAllowedActions prototype, UsernamePrincipal owner) {
        disableAccessControl(prototype, owner, Collections.emptyList());
    }
    
    default void disableAccessControl(JcrAllowedActions prototype, UsernamePrincipal owner, List<SecurityRole> roles) {
        JcrAllowedActions allowed = getJcrAllowedActions();
        prototype.copy(allowed.getNode(), owner);
        allowed.removeAccessControl(owner);
        
        if (roles.isEmpty()) {
            JcrRoleMembership.removeAll(getNode());
        } else {
            roles.forEach(role -> JcrRoleMembership.remove(getNode(), ((JcrSecurityRole) role).getNode()));
        }
    }

    default void enableAccessControl(JcrAllowedActions prototype, UsernamePrincipal owner, List<SecurityRole> roles) {
        JcrAllowedActions allowed = getJcrAllowedActions();
        prototype.copy(allowed.getNode(), owner, Privilege.JCR_ALL);
        allowed.setupAccessControl(owner);
        
        roles.forEach(role -> JcrRoleMembership.create(getNode(), ((JcrSecurityRole) role).getNode(), allowed));
    }

    Class<? extends JcrAllowedActions> getJcrAllowedActionsType();
}
