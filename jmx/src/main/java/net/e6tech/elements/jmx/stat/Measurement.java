/*
Copyright 2015 Futeh Kao

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

package net.e6tech.elements.jmx.stat;

import net.e6tech.elements.common.util.datastructure.BinarySearchList;

import java.io.Serializable;
import java.util.LinkedList;


/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S00116", "squid:S00117"})
public class Measurement implements Serializable, MeasurementMXBean {

    private static final long serialVersionUID = -5888966219198957050L;
    private String name;
    private String unit;
    private long count = 0;
    private long total = 0;
    private double average = 0.0;
    private double median = 0.0;
    private double sum =0.0;
    private double sum_x_2 = 0.0;  //i.e. sum of x^2, which is not sum^2!!!
    private double stdDev = 0.0;
    private long windowWidth = 300000l;  // default is 5 minutes
    private int windowMaxCount = 0;     // limits the total number of samples in the window
    private boolean dirty = false;
    private boolean enabled = true;
    protected transient LinkedList<DataPoint> sortedByTime = new LinkedList<>(); // sorted by timestamp
    protected transient LinkedList<Long> failures = new LinkedList<>();
    protected transient BinarySearchList<DataPoint> sortedByValue = new BinarySearchList<>(); // sorted by value

    public Measurement() {}

    public Measurement(boolean enabled) {
        this.enabled = enabled;
    }

    public Measurement(String name, String unit, boolean enabled) {
        this.name = name;
        this.unit = unit;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public long getTotal() {
        return total;
    }

    public long getCount() {
        if (dirty)
            recalculate();
        return count;
    }

    public double getAverage() {
        if (dirty)
            recalculate();
        return average;
    }

    public double getMedian() {
        if (dirty)
            recalculate();
        return median;
    }

    public double getSum() {
        if (dirty)
            recalculate();
        return sum;
    }

    public double getStdDev() {
        if (dirty)
            recalculate();
        return stdDev;
    }

    public long getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(long windowWidth) {
        this.windowWidth = windowWidth;
    }

    public int getWindowMaxCount() {
        return windowMaxCount;
    }

    public void setWindowMaxCount(int windowMaxCount) {
        this.windowMaxCount = windowMaxCount;
    }

    public long getFailureCount() {
        if (dirty)
            recalculate();
        return failures.size();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public synchronized void fail() {
        if (!isEnabled())
            return;
        failures.add(System.currentTimeMillis());
        dirty = true;
        trimFailures();
    }

    /**
     * Because dataPoints is transient, we have to record everything.
     * @param value a measurement
     */
    public synchronized void add(double value) {
        if (!isEnabled())
            return;
        total ++;
        add(new DataPoint(System.currentTimeMillis(), value));
    }

    public Measurement append(double value) {
        if (!isEnabled())
            return this;
        add(value);
        return this;
    }

    protected synchronized void recalculate() {
        trimFailures();
        trimData();

        count = sortedByTime.size();

        // calculating average and median
        if (count == 0) {
            average = 0.0;
            median = 0.0;
        } else {
            average = sum / count;
            int index = (int) count / 2;
            if (count == 2 * index) {
                DataPoint dp1 = sortedByValue.get(index);
                DataPoint dp2 =  sortedByValue.get(index - 1);
                median = (dp1.getValue() + dp2.getValue()) / 2f;
            } else {
                median = (sortedByValue.get(index)).getValue();
            }
        }

        // Sample standard deviation
        if (count > 1) {
            double n_ave_2 = count * average * average;
            double var = (sum_x_2 - n_ave_2) / (double)(count - 1);
            stdDev = Math.sqrt(var);
        } else {
            stdDev = 0.0;
        }

        dirty = false;
    }

    protected synchronized void add(DataPoint dp) {
        if (!isEnabled())
            return;
        sortedByTime.add(dp);
        sortedByValue.add(dp);

        double value = dp.getValue();
        sum += value;
        sum_x_2 += (value * value);
        dirty = true;
        trimData();
    }

    private synchronized void trimData() {
        long expire = System.currentTimeMillis() - windowWidth;
        while (!sortedByTime.isEmpty() && sortedByTime.getFirst().getTimestamp() < expire) {
            remove();
        }

        while (windowMaxCount > 0 && sortedByTime.size() > windowMaxCount) {
            remove();
        }
    }

    private synchronized void trimFailures() {
        long expire = System.currentTimeMillis() - windowWidth;
        while (!failures.isEmpty() && failures.getFirst() < expire) {
            failures.remove();
        }

        while (windowMaxCount > 0 && failures.size() > windowMaxCount) {
            failures.remove();
        }
    }

    protected DataPoint remove() {
        if (!sortedByTime.isEmpty()) {
            DataPoint dp = sortedByTime.removeFirst();
            sortedByValue.removeFirst(dp);
            double removed = dp.getValue();
            sum -= removed;
            sum_x_2 = sum_x_2 - (removed * removed);
            dirty = true;
            return dp;
        }
        return null;
    }

    public String toString() {
        if (dirty)
            recalculate();

        StringBuilder builder = new StringBuilder();
        builder.append("count=" + count + " ");
        builder.append("average=" + average + " ");
        builder.append("stddev=" + stdDev + " ");
        builder.append("failureCount=" + failures.size() + " ");
        builder.append("windowWidth=" + windowWidth + " ");
        return builder.toString();
    }
}
