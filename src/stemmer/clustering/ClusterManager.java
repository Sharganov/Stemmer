package stemmer.clustering;

import stemmer.yass.DistanceMeasure;

import java.util.*;
import java.util.concurrent.ForkJoinPool;

/**
 * Class that deals with managing clusters and the related distance matrix.
 * */
class ClusterManager {

    static ForkJoinPool commonPool = new ForkJoinPool();

    private List<Cluster> clusters;
    private DistanceMeasure d;
    MyCustomBigArray dist;


    /* Formulas for converting indexes (note that the array is flipped, so you need to reverse k)
    * i = n - 2 - floor(sqrt(-8*k + 4*n*(n-1)-7)/2.0 - 0.5)
    * j = k + i + 1 - n*(n-1)/2 + (n-i)*((n-i)-1)/2
    * k = (n*(n-1)/2) - (n-i)*((n-i)-1)/2 + j - i - 1
    * */
    long _k(int i, int j){
        long n = clusters.size();
        long k = (n *(n-1))/2 - ( (n-i)*(n-i-1) )/2 + j - i - 1;
        k = ( n*(n-1) )/2 - 1 - k;
        return k;
    }

    int _i(long k){
        long n = clusters.size();
        k = ( n*(n-1) )/2 - 1 - k; // re-form k
        long i = n - 2 - (int)Math.floor(Math.sqrt(-8*k + 4*n*(n-1)-7)/2 - 0.5);
        return  (int)i; // i
    }

    int _j(long k){
        long n = clusters.size();
        int i = _i(k); // _i turns k, so I have to calculate it first
        k = (n*(n-1))/2 - 1 - k; // ritrasformo k
        long j =  (k + i + 1 - (n*(n-1))/2 + ((n-i)*((n-i)-1))/2); // j
        return (int)j;
    }

    /**
     * Create a new cluster manager in {@code clusters}, using {@code d} as a distance measure.
     * @param clusters clusters to be included in the manager.
     * @param d distance measure to be used to define the matrix.
     * */
    ClusterManager(List<Cluster> clusters, DistanceMeasure d) {
        this.clusters = clusters;
        this.d = d;

        long n = clusters.size();
        long tot = (n*(n-1))/2;
        dist = new MyCustomBigArray(tot);
        System.out.println("create the matrix of distances ...");

        long startTime = System.currentTimeMillis();
        BuildDistanceMatrixTask.buildDistanceMatrix(this, d);
        System.out.println("End of matrix creation. Necessary time"
                                +(System.currentTimeMillis()-startTime)/1000 +" s");
    }


    void deleteClusters(List<Integer> indexes){


        Collections.sort(indexes);
        //actuallyDeleteClusters(indexes);
        int start;
        int CHUNK_SIZE = 7;
        for (start = 0; start + CHUNK_SIZE < indexes.size(); start+= CHUNK_SIZE){
            List<Integer> ar = new ArrayList<>();
            for (Integer i:  indexes.subList(start, start+CHUNK_SIZE)) {
                ar.add(i);
            }
            actuallyDeleteClusters(ar);
            // have to add the indices
            for (int q = start+CHUNK_SIZE; q < indexes.size(); q++){
                indexes.set(q, indexes.get(q) - CHUNK_SIZE);
            }
        }
        List<Integer> ar = new ArrayList<>();
        for (Integer i:  indexes.subList(start, indexes.size())) {
            ar.add(i);
        }
        try {
            if(ar.size() > 0)
                actuallyDeleteClusters(ar);
        } catch (Exception e) {
            System.out.println(e.toString());
            System.out.println();
        }
    }

    private void actuallyDeleteClusters(List<Integer> indexes) {
        int n = clusters.size();
        // The same pair may appear more than once
        //calculate the torque index in the linearized array.
        Set<Long> toDelete = new HashSet<>();
        //List<Long> toDeleteIndexes = new ArrayList<>();
        // Per ogni indice calcolo le coppie in cui compare
        for (int r : indexes) {
            // calcolo le coppie del tipo (*,r)
            for (int i = 0; i < r; i++) {
                long index = _k(i,r);
                if (index >= 0 && index < dist.getSize() ){
                    toDelete.add(index);
                }
            }
            // calculate the pairs of the type (r, *) (there is (r, s))
            // they are consecutive and there are n-r-1s
            for (int j = r+1; j < r+1+(n-r-1); j++) {
                long index = _k(r,j);
                if (index >= 0 && index < dist.getSize()){
                    toDelete.add(index);
                }
            }
        }

        List<Long> toDeleteIndexes = new ArrayList<>();
        toDeleteIndexes.addAll(toDelete);
        // order the indexes to be deleted in descending order
        Collections.sort(toDeleteIndexes);

      // Overwrite the values to be deleted by compacting the vector.
        long tot = ((long)n*(n-1))/2;
        int cntDeleted = 0;

        for (long it = 0; it < tot; it++) {
            // If have not erased anything yet or not
            // delete the current index, step to the next item
            if (cntDeleted == 0 && it != toDeleteIndexes.get(cntDeleted)) { continue; }

            //if (cntDeleted < toDelete.size() && it == toDeleteIndexes.get(cntDeleted))
            if (cntDeleted < toDeleteIndexes.size() && it == toDeleteIndexes.get(cntDeleted))
                cntDeleted += 1;

            //Before copying the next index, check that you do not copy
            //an index that then must be deleted
            //while (cntDeleted < toDelete.size() && it + cntDeleted == toDeleteIndexes.get(cntDeleted))
            while (cntDeleted < toDeleteIndexes.size() && it + cntDeleted == toDeleteIndexes.get(cntDeleted))

                cntDeleted += 1;

            if (it + cntDeleted < tot)
                dist.set(it, dist.get(it+cntDeleted));
            else
                break;
        }

        // Delete the clusters also from the list
        for (int i = 0; i < indexes.size(); i++) {
            int adjustedIndex = indexes.get(i) - i;
            clusters.remove(adjustedIndex);
        }
    }


    void insert(Cluster cluster){
        clusters.add(0, cluster);
        int n = clusters.size();


        int i = 0;  // entered the cluster in the head, has index 0
        for (int j = i+1; j < n; j++) {
            long k = _k(i, j);
            dist.set(k, clusters.get(i).distance(clusters.get(j), d));
        }
    }


    List<MinDistancePair> findMinDistancePairs() {
        return FindMinDistancePairTask.findMinDistancePairs(this);
    }

    int size() {
        return clusters.size();
    }

    Cluster getCluster(int i){
        return clusters.get(i);
    }

    void resize() {
        long n = this.clusters.size();
        long newSize = (n*(n-1))/2;
        dist.resize(newSize);
    }
}
