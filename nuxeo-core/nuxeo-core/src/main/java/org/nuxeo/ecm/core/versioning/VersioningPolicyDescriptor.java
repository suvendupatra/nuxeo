/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
 *
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
 *
 * Contributors:
 *     Funsho David
 *     Kevin Leturc
 */
package org.nuxeo.ecm.core.versioning;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.VersioningOption;

/**
 * @since 9.1
 */
@XObject("policy")
public class VersioningPolicyDescriptor implements Serializable {

    @XNode("@id")
    protected String id;

    @XNode("@increment")
    protected VersioningOption increment;

    @XNode("initialState")
    public InitialStateDescriptor initialState;

    @XNodeList(value = "filterId", componentType = String.class, type = ArrayList.class)
    protected List<String> filterIds;

    public String getId() {
        return id;
    }

    public VersioningOption getIncrement() {
        return increment;
    }

    public InitialStateDescriptor getInitialState() {
        return initialState;
    }

    public List<String> getFilterIds() {
        return filterIds;
    }

    public void merge(VersioningPolicyDescriptor other) {
        if (other.id != null) {
            id = other.id;
        }
        if (other.increment != null) {
            increment = other.increment;
        }
        if (other.initialState != null) {
            initialState = other.initialState;
        }
        filterIds.addAll(other.filterIds);
    }
}
