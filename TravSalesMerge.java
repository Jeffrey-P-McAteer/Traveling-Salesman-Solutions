import java.util.stream.*;
import java.util.*;
import java.io.*;

/**
 * Suggestion: for large paths (>1,000 cities) pass the -Xss128m argument
 * to the JVM to allocate more stack memory. This solution is highly recursive.
 * if the heap becomes an issue, the flag for that is -Xmx128m
 * 
 * @author Jeffrey McAteer
 */
public class TravSalesMerge {
  public static int[][] locationCoords;
  
  public static void main(String... args) throws Exception {
    // Note that MS systems appear to break this method of reading a file, it may be easier
    // to hard-code the city data in these cases.
    String citiesData = new Scanner(new File("cities.txt")).useDelimiter("\\Z").next();
    String[] cities = citiesData.split("\n");
    locationCoords = new int[cities.length][2];
    IntStream.range(0, cities.length)
      .forEach(i -> {
        String[] xAndY = cities[i].split(" ");
        IntStream.range(0, xAndY.length)
          .forEach(j -> locationCoords[i][j] = Integer.parseInt(xAndY[j]));
      });
    // locationCoords is now populated with each city's x and y coord
    Integer[] path = Stream.iterate(0, n -> n + 1).limit(locationCoords.length).toArray(i -> new Integer[i]);
    
    // System.out.printf("Stats:\ncores %d\n\n",
    //   Runtime.getRuntime().availableProcessors()
    // );
    
    System.out.printf("Original Path length: %d\n", pathLength(path));
    //System.out.println(Arrays.asList(path));
    
    solve(path);
    
    System.out.printf("Merge-Solve (recursive) length: %d\n", pathLength(path));
    System.out.printf("Took %d steps for length of %d\n", steps, path.length);
    System.out.println(Arrays.asList(path));
    
  }
  
  public static int steps = 0;
  
  public static void solve(Integer[] path) {
    int ignoreLevel = 3;
    while (true) {
      try {
        solve(path, ignoreLevel);
        if (ignoreLevel > 3) {
          System.out.println("had to set an ignoreLevel of "+ignoreLevel);
        }
        break;
      } catch (StackOverflowError e) {
        ignoreLevel++;
        steps = 0;
      }
    }
  }
  
  public static void solve(Integer[] path, int ignoreLevel) { steps += 2; // we both split and merge in this method
    if (path.length < ignoreLevel) return; // can we avoid a stack overflow for a partial solution?
    if (path.length < 3) return; // paths of size 2 or smaller are already 'solved'
    // split the path into 4 paths on each quadrant
    Integer[][] quads = split(path);
    for (Integer[] quadPath : quads) {
      // solve that quadrant's path
      solve(quadPath, ignoreLevel);
    }
    // this only works because we have a _refrence_ which we operate on.
    // After everything has been split up, we merge them back together
    moveFrom(merge(quads), path);
  }
  
  // merge the paths in quadrants together into a single path
  public static Integer[] merge(Integer[][] paths) {
    if (paths.length == 1) {
      return paths[0];
      
    } else if (paths.length == 2) {
      // conn is a number telling us which of the 4 possible orientations
      // is the closest to connect two graphs.
      int conn = closestPointsConnectingNum(paths[0], paths[1]);
      switch (conn) {
        case 0:
          // stick the beginning of the first path to the beginning of the second path
          return concatenate(reverse(paths[0]), paths[1]);
        case 1:
          // stick the beginning of the first path to the end of the second path
          return concatenate(reverse(paths[0]), reverse(paths[1]));
        case 2:
          // stick the end of the first path to the beginning of the second path
          return concatenate(paths[0], paths[1]);
        case 3:
          // stick the end of the first path to the end of the second path
          return concatenate(paths[0], reverse(paths[1]));
      }
      assert false:"something borked";
      return null;
    } else {
      // if more than 2, merge the first and the merged result of the remaining ones.
      return merge(new Integer[][]{
        paths[0],
        merge(Arrays.copyOfRange(paths, 1, paths.length))
      });
    }
  }
  
  // split the path into 4 quadrants of paths
  public static Integer[][] split(Integer[] path) {
    if (path.length < 1) return null;
    Integer[][] quads = new Integer[4][path.length]; // originally had path.length/4, but we remove nulls anyway
    int[] quad_i = new int[]{0, 0, 0, 0}; // indexes for quads
    int[] center = center(path);
    for (Integer i : path) {
      int quad = quadrant(center, locationCoords[i]);
      quads[quad][quad_i[quad]] = i;
      quad_i[quad]++;
    }
    // quadrants may have nulls on the end
    for (int i=0; i<quads.length; i++) {
      quads[i] = removeNulls(quads[i]);
    }
    return quads;
  }
  
  // returns 0-3, depending upon the comparison of 4 distance operations
  public static int closestPointsConnectingNum(Integer[] pathA, Integer[] pathB) {
    if (pathA.length < 1 || pathB.length < 1) {
      return 0;
    }
    double[] dists = new double[4];
    dists[0] = fastDistance(locationCoords[pathA[0]], locationCoords[pathB[0]]);
    dists[1] = fastDistance(locationCoords[pathA[0]], locationCoords[pathB[pathB.length-1]]);
    dists[2] = fastDistance(locationCoords[pathA[pathA.length-1]], locationCoords[pathB[0]]);
    dists[3] = fastDistance(locationCoords[pathA[pathA.length-1]], locationCoords[pathB[pathB.length-1]]);
    int closestDistIndex = 0;
    for (int i=0; i<dists.length; i++) {
      if (dists[i] > dists[closestDistIndex]) {
        closestDistIndex = i;
      }
    }
    return closestDistIndex;
  }
  
  // quad of 0, 1, 2, or 3. 0 is in upper right, 1 is in upper left, 2 lower left, 3 lower right
  // NB: 0,0 is in quad 1. Basically, values of 0 round upwards towards 1
  // quadrants are relative to the origin coordinates
  public static int quadrant(int[] origin, int[] coord) {
    if (coord[0] >= origin[0] && coord[1] >= origin[1]) {
      // x is 'positive' and y is 'positive'
      return 0;
    } else if (coord[0] >= origin[0] && coord[1] < origin[1]) {
      // x is 'positive' and y is 'negative'
      return 3;
    } else if (coord[0] < origin[0] && coord[1] >= origin[1]) {
      // x is 'negative' and y is 'positive'
      return 1;
    } else { // neg and neg
      return 2;
    }
  }
  
  // get coordinates of the center of this path (or sub-path)
  public static int[] center(Integer[] path) {
    assert path.length > 0;
    double weight = 1.0/path.length;
    double[] center = new double[] {0.0, 0.0};
    for (Integer i : path) {
      center[0] += weight * locationCoords[i][0];
      center[1] += weight * locationCoords[i][1];
    }
    return new int[] {(int) center[0], (int) center[1]};
  }
  
  // not real length, is inaccurate but comparable
  public static int fastPathLength(Integer[] path) {
    double totalLen = 0;
    for (int i=0; i<path.length-1; i += 2) {
      int firstCity = path[i];
      int secondCity = path[i+1];
      totalLen += fastDistance(locationCoords[firstCity], locationCoords[secondCity]);
    }
    int firstCity = path[0];
    int secondCity = path[path.length-1];
    totalLen += fastDistance(locationCoords[firstCity], locationCoords[secondCity]);
    return (int) totalLen;
  }
  
  public static int pathLength(Integer[] path) {
    double totalLen = 0;
    for (int i=0; i<path.length-1; i += 2) {
      int firstCity = path[i];
      int secondCity = path[i+1];
      totalLen += distance(locationCoords[firstCity], locationCoords[secondCity]);
    }
    int firstCity = path[0];
    int secondCity = path[path.length-1];
    totalLen += distance(locationCoords[firstCity], locationCoords[secondCity]);
    return (int) totalLen;
  }
  
  // not real distance, is inaccurate but comparable
  public static double fastDistance(int[] coord1, int[] coord2) {
    int[] delta = new int[] {
      coord1[0] - coord2[0],
      coord1[1] - coord2[1]
    };
    return (delta[0]*delta[0]) + (delta[1]*delta[1]);
  }
  
  public static double distance(int[] coord1, int[] coord2) {
    int[] delta = new int[] {
      coord1[0] - coord2[0],
      coord1[1] - coord2[1]
    };
    return Math.sqrt(Math.pow(delta[0], 2) + Math.pow(delta[1], 2));
  }
  
  public static Integer[] clone(Integer[] nums) {
    Integer[] newNums = new Integer[nums.length];
    moveFrom(nums, newNums);
    return newNums;
  }
  
  public static void moveFrom(Integer[] a, Integer[] b) {
    for (int i=0; i<a.length; i++) {
      b[i] = a[i];
    }
  }
  
  public static Integer[] removeNulls(Integer[] i) {
    int firstNullIndex = 0;
    while (firstNullIndex < i.length && i[firstNullIndex] != null) firstNullIndex++;
    return Arrays.copyOfRange(i, 0, firstNullIndex);
  }
  
  public static <T> T[] reverse(T[] a) {
    // stole & modified from stackoverflow.com/a/2137791
    for(int i = 0; i < a.length / 2; i++) {
      T temp = a[i];
      a[i] = a[a.length - i - 1];
      a[a.length - i - 1] = temp;
    }
    return a;
  }
  
  // stolen at stackoverflow.com/a/80503, courtesy of jeannicolas
  public static <T> T[] concatenate (T[] a, T[] b) {
    int aLen = a.length;
    int bLen = b.length;
    
    @SuppressWarnings("unchecked")
    T[] c = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), aLen+bLen);
    System.arraycopy(a, 0, c, 0, aLen);
    System.arraycopy(b, 0, c, aLen, bLen);
    
    return c;
  }
  
}
