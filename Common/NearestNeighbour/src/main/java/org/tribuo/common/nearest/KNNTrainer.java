/*
 * Copyright (c) 2015-2021, Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tribuo.common.nearest;

import com.oracle.labs.mlrg.olcut.config.Config;
import com.oracle.labs.mlrg.olcut.config.PropertyException;
import com.oracle.labs.mlrg.olcut.provenance.Provenance;
import com.oracle.labs.mlrg.olcut.util.Pair;
import org.tribuo.Dataset;
import org.tribuo.Example;
import org.tribuo.ImmutableFeatureMap;
import org.tribuo.ImmutableOutputInfo;
import org.tribuo.Model;
import org.tribuo.Output;
import org.tribuo.Trainer;
import org.tribuo.common.nearest.KNNModel.Backend;
import org.tribuo.ensemble.EnsembleCombiner;
import org.tribuo.math.la.SparseVector;
import org.tribuo.provenance.ModelProvenance;
import org.tribuo.provenance.TrainerProvenance;
import org.tribuo.provenance.impl.TrainerProvenanceImpl;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * A {@link Trainer} for k-nearest neighbour models.
 */
public class KNNTrainer<T extends Output<T>> implements Trainer<T> {

    /**
     * The available distance functions.
     */
    public enum Distance {
        /**
         * L1 (or Manhattan) distance.
         */
        L1,
        /**
         * L2 (or Euclidean) distance.
         */
        L2,
        /**
         * Cosine similarity used as a distance measure.
         */
        COSINE
    }

    @Config(mandatory = true, description="The distance function used to measure nearest neighbours.")
    private Distance distance;

    @Config(mandatory = true, description="The number of nearest neighbours to check.")
    private int k;

    @Config(mandatory = true, description="The combination function to aggregate the nearest neighbours.")
    private EnsembleCombiner<T> combiner;

    @Config(description="The number of threads to use for inference.")
    private int numThreads = 1;

    @Config(description="The threading model to use.")
    private Backend backend = Backend.THREADPOOL;

    private int trainInvocationCount = 0;

    /**
     * For olcut.
     */
    private KNNTrainer() {}

    /**
     * Creates a K-NN trainer using the supplied parameters.
     * @param k The number of nearest neighbours to consider.
     * @param distance The distance function.
     * @param numThreads The number of threads to use.
     * @param combiner The combination function to aggregate the k predictions.
     * @param backend The computational backend.
     */
    public KNNTrainer(int k, Distance distance, int numThreads, EnsembleCombiner<T> combiner, Backend backend) {
        this.k = k;
        this.distance = distance;
        this.numThreads = numThreads;
        this.combiner = combiner;
        this.backend = backend;
        postConfig();
    }

    /**
     * Used by the OLCUT configuration system, and should not be called by external code.
     */
    @Override
    public void postConfig() {
        if (k < 1) {
            throw new PropertyException("","k","k must be greater than 0");
        }
    }

    @Override
    public Model<T> train(Dataset<T> examples, Map<String, Provenance> runProvenance) {
        return(train(examples, runProvenance, INCREMENT_INVOCATION_COUNT));
    }

    @Override
    public Model<T> train(Dataset<T> examples, Map<String, Provenance> runProvenance, int invocationCount) {
        ImmutableFeatureMap featureIDMap = examples.getFeatureIDMap();
        ImmutableOutputInfo<T> labelIDMap = examples.getOutputIDInfo();

        @SuppressWarnings("unchecked") // generic array creation
        Pair<SparseVector,T>[] vectors = new Pair[examples.size()];

        int i = 0;
        for (Example<T> e : examples) {
            vectors[i] = new Pair<>(SparseVector.createSparseVector(e,featureIDMap,false),e.getOutput());
            i++;
        }

        if(invocationCount != INCREMENT_INVOCATION_COUNT){
            setInvocationCount(invocationCount);
        }
        trainInvocationCount++;

        ModelProvenance provenance = new ModelProvenance(KNNModel.class.getName(), OffsetDateTime.now(), examples.getProvenance(), getProvenance(), runProvenance);

        return new KNNModel<>(k+"nn",provenance, featureIDMap, labelIDMap, false, k, distance, numThreads, combiner, vectors, backend);
    }

    @Override
    public String toString() {
        return "KNNTrainer(k="+k+",distance="+distance+",combiner="+combiner.toString()+",numThreads="+numThreads+")";
    }

    @Override
    public int getInvocationCount() {
        return trainInvocationCount;
    }

    @Override
    public void setInvocationCount(int invocationCount) {
        if(invocationCount < 0){
            throw new IllegalArgumentException("The supplied invocationCount is less than zero.");
        }

        this.trainInvocationCount = invocationCount;
    }

    @Override
    public TrainerProvenance getProvenance() {
        return new TrainerProvenanceImpl(this);
    }
}
