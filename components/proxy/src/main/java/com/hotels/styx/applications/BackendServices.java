/*
  Copyright (C) 2013-2018 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.applications;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.BackendService;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getFirst;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.service.BackendService.newBackendServiceBuilder;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * BackendServices represents the collection of {@link BackendService}s that a styx instance can proxy to.
 * <br />
 * When loaded from YAML or JSON, it also sets some derived attributes that are not included in the YAML/JSON due to redundancy:
 * <br />
 * For each origin in {@link BackendService#origins()}, it derives {@link com.hotels.styx.api.extension.Origin#applicationId()} from {@link BackendService#id()}
 */
public final class BackendServices implements Iterable<BackendService> {
    private final Collection<BackendService> backendServices;

    @JsonCreator
    BackendServices(@JsonProperty("applications") Collection<BackendService> backendServices) {
        this(backendServices, true);
    }

    private BackendServices(Collection<BackendService> backendServices, boolean setDerivedAttributes) {
        checkArgument(noDuplicateIds(backendServices), "Duplicate ids in " + backendServices);

        if (setDerivedAttributes) {
            // note: ImmutableSet preserves order
            this.backendServices = ImmutableSet.copyOf(backendServices.stream().map(BackendServices::setDerivedAttributes).collect(toList()));
        } else {
            this.backendServices = backendServices;
        }
    }

    private static boolean noDuplicateIds(Collection<BackendService> backendServices) {
        Set<Id> ids = backendServices.stream().map(BackendService::id).collect(toSet());

        return ids.size() == backendServices.size();
    }

    /**
     * Creates a new Applications object.
     *
     * @param applications applications
     * @return a new Applications
     */
    public static BackendServices newBackendServices(Iterable<BackendService> applications) {
        return new BackendServices(ImmutableSet.copyOf(applications), true);
    }

    /**
     * Creates a new Applications object.
     *
     * @param backendServices applications
     * @return a new Applications
     */
    public static BackendServices newBackendServices(BackendService... backendServices) {
        return new BackendServices(ImmutableSet.copyOf(backendServices), true);
    }

    private static BackendService setDerivedAttributes(BackendService application) {
        return newBackendServiceBuilder(application)
                .origins(originsWithDerivedApplicationIds(application))
                .build();
    }

    private static Set<Origin> originsWithDerivedApplicationIds(BackendService backendService) {
        return ImmutableSet.copyOf(originsWithApplicationId(backendService));
    }

    private static Iterable<Origin> originsWithApplicationId(BackendService backendService) {
        return backendService.origins().stream().map(origin ->
                newOriginBuilder(origin)
                        .applicationId(backendService.id())
                        .build())
                .collect(toList());
    }

    @JsonProperty("applications")
    Collection<BackendService> applications() {
        return backendServices;
    }

    /**
     * All origins from all applications represented by this object.
     *
     * @return all origins
     */
    public Iterable<Origin> origins() {
        return backendServices.stream()
                .map(BackendService::origins)
                .flatMap(Collection::stream)
                .collect(toList());
    }

    @Override
    public Iterator<BackendService> iterator() {
        return backendServices.iterator();
    }

    /**
     * Returns the first application from this object.
     *
     * @return the first application
     */
    public BackendService first() {
        BackendService first = getFirst(this, null);
        if (first == null) {
            throw new NoSuchElementException();
        }
        return first;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("backendServices", backendServices)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(backendServices);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BackendServices other = (BackendServices) obj;
        return Objects.equal(this.backendServices, other.backendServices);
    }
}
