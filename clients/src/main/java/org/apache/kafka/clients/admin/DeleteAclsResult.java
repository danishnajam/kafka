/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.clients.admin;

import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.annotation.InterfaceStability;
import org.apache.kafka.common.errors.ApiException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The result of the {@link AdminClient#deleteAcls(Collection)} call.
 *
 * The API of this class is evolving, see {@link AdminClient} for details.
 */
@InterfaceStability.Evolving
public class DeleteAclsResult {

    /**
     * A class containing either the deleted ACL or an exception if the delete failed.
     */
    public static class FilterResult {
        private final AclBinding acl;
        private final ApiException exception;

        FilterResult(AclBinding acl, ApiException exception) {
            this.acl = acl;
            this.exception = exception;
        }

        /**
         * Return the deleted ACL or null if there was an error.
         */
        public AclBinding acl() {
            return acl;
        }

        /**
         * Return an exception if the ACL delete was not successful or null if it was.
         */
        public ApiException exception() {
            return exception;
        }
    }

    /**
     * A class containing the results of the delete ACLs operation.
     */
    public static class FilterResults {
        private final List<FilterResult> acls;

        FilterResults(List<FilterResult> acls) {
            this.acls = acls;
        }

        /**
         * Return a list of delete ACLs results.
         */
        public List<FilterResult> acls() {
            return acls;
        }
    }

    private final Map<AclBindingFilter, KafkaFuture<FilterResults>> futures;

    DeleteAclsResult(Map<AclBindingFilter, KafkaFuture<FilterResults>> futures) {
        this.futures = futures;
    }

    /**
     * Return a map from acl filters to futures which can be used to check the status of the deletions by each
     * filter.
     */
    public Map<AclBindingFilter, KafkaFuture<FilterResults>> results() {
        return futures;
    }

    /**
     * Return a future which succeeds only if all the ACLs deletions succeed, and which contains all the deleted ACLs.
     * Note that it if the filters don't match any ACLs, this is not considered an error.
     */
    public KafkaFuture<Collection<AclBinding>> all() {
        return KafkaFuture.allOf(futures.values().toArray(new KafkaFuture[0])).thenApply(
            new KafkaFuture.Function<Void, Collection<AclBinding>>() {
                @Override
                public Collection<AclBinding> apply(Void v) {
                    List<AclBinding> acls = new ArrayList<>();
                    for (Map.Entry<AclBindingFilter, KafkaFuture<FilterResults>> entry : futures.entrySet()) {
                        FilterResults results;
                        try {
                            results = entry.getValue().get();
                        } catch (Throwable e) {
                            // This should be unreachable, since the future returned by KafkaFuture#allOf should
                            // have failed if any Future failed.
                            throw new KafkaException("DeleteAclsResult#all: internal error", e);
                        }
                        for (FilterResult result : results.acls()) {
                            if (result.exception() != null) {
                                throw result.exception();
                            }
                            acls.add(result.acl());
                        }
                    }
                    return acls;
                }
            });
    }
}
