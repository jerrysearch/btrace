/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.btrace.services.impl;

import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.services.spi.SimpleService;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Formatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * A simple way to submit <a href="https://github.com/etsy/statsd/">statsd</a> metrics.
 * <p>
 * Use the following code to obtain an instance:
 * <pre>
 * <code>
 * {@literal @}Injected(factoryMethod = "getInstance")
 *   private static Statsd s;
 * </code>
 * </pre>
 * @author Jaroslav Bachorik
 */
final public class Statsd extends SimpleService {
    private final static String STATSD_HOST = "com.sun.btrace.statsd.host";
    private final static String STATSD_PORT = "com.sun.btrace.statsd.port";

    public static enum Priority {
        NORMAL, LOW
    }
    public static enum AlertType {
        INFO, WARNING, ERROR, SUCCESS
    }

    private final static class Singleton {
        private final static Statsd INSTANCE = new Statsd();
    }

    private final BlockingQueue<String> q = new ArrayBlockingQueue<>(120000);
    private final ExecutorService e = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "BTrace Statsd Submitter");
            t.setDaemon(true);
            return t;
        }
    });

    public static Statsd getInstance() {
        return Singleton.INSTANCE;
    }

    private Statsd() {
        e.submit(new Runnable() {
            @Override
            public void run() {
                DatagramSocket ds = null;
                boolean entered = BTraceRuntime.enter();
                try {
                    ds = new DatagramSocket();
                    DatagramPacket dp = new DatagramPacket(new byte[0], 0);
                    try {
                        dp.setAddress(InetAddress.getByName(System.getProperty(STATSD_HOST, "localhost")));
                    } catch (UnknownHostException e) {
                        System.err.println("[statsd] invalid host defined: " + System.getProperty(STATSD_HOST));
                        dp.setAddress(InetAddress.getLoopbackAddress());
                    } catch (SecurityException e) {
                        dp.setAddress(InetAddress.getLoopbackAddress());
                    }
                    try {
                        int port = Integer.parseInt(System.getProperty(STATSD_PORT, "8125"));
                        dp.setPort(port);
                    } catch (NumberFormatException e) {
                        System.err.println("[statsd] invalid port defined: " + System.getProperty(STATSD_PORT));
                        dp.setPort(8125);
                    }

                    while (true) {
                        String m = q.take();
                        dp.setData(m.getBytes());
                        ds.send(dp);
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    if (entered) {
                        BTraceRuntime.leave();
                    }
                }
            }
        });
    }

    /**
     * Increase the given counter by 1
     * @param name the counter name
     */
    public void increment(String name) {
        count(name, 1);
    }

    /**
     * Increase the given counter by 1
     * @param name the counter name
     * @param tags
     *     Only for DogStatsD compatible collectors.
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void increment(String name, String tags) {
        count(name, 1, tags);
    }

    /**
     * Increase the given counter by 1
     * @param name the counter name
     * @param sampleRate
     *     the sampling rate being employed. For example, a rate of 0.1 would
     *     tell StatsD that this counter is being sent
     *     sampled every 1/10th of the time.
     */
    public void increment(String name, double sampleRate) {
        count(name, 1, sampleRate);
    }

    /**
     * Increase the given counter by 1
     * @param name the counter name
     * @param sampleRate
     *     the sampling rate being employed. For example, a rate of 0.1 would
     *     tell StatsD that this counter is being sent
     *     sampled every 1/10th of the time.
     * @param tags
     *     Only for DogStatsD compatible collectors.
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void increment(String name, double sampleRate, String tags) {
        count(name, 1, sampleRate, tags);
    }

    /**
     * Decrease the given counter by 1
     * @param name the counter name
     */
    public void decrement(String name) {
        count(name, -1);
    }

    /**
     * Decrease the given counter by 1
     * @param name the counter name
     * @param tags
     *     Only for DogStatsD compatible collectors.
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void decrement(String name, String tags) {
        count(name, -1, tags);
    }

    /**
     * Decrease the given counter by 1
     * @param name the counter name
     * @param sampleRate
     *     the sampling rate being employed. For example, a rate of 0.1 would
     *     tell StatsD that this counter is being sent
     *     sampled every 1/10th of the time.
     */
    public void decrement(String name, double sampleRate) {
        count(name, -1, sampleRate);
    }

    /**
     * Decrease the given counter by 1
     * @param name the counter name
     * @param sampleRate
     *     the sampling rate being employed. For example, a rate of 0.1 would
     *     tell StatsD that this counter is being sent
     *     sampled every 1/10th of the time.
     * @param tags
     *     Only for DogStatsD compatible collectors.
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void decrement(String name, double sampleRate, String tags) {
        count(name, -1, sampleRate, tags);
    }

    /**
     * Adjusts the specified counter by a given delta.
     *
     * @param name
     *     the name of the counter to adjust
     * @param delta
     *     the amount to adjust the counter by
     */
    public void count(String name, long delta) {
        count(name, delta, null);
    }

    /**
     * Adjusts the specified counter by a given delta.
     *
     * @param name
     *     the name of the counter to adjust
     * @param delta
     *     the amount to adjust the counter by
     * @param tags
     *     Only for DogStatsD compatible collectors.
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void count(String name, long delta, String tags) {
        count(name, delta, 0d, tags);
    }

    /**
     * Adjusts the specified counter by a given delta.
     *
     * @param name
     *     the name of the counter to adjust
     * @param delta
     *     the amount to adjust the counter by
     * @param sampleRate
     *     the sampling rate being employed. For example, a rate of 0.1 would
     *     tell StatsD that this counter is being sent
     *     sampled every 1/10th of the time.
     */
    public void count(String name, long delta, double sampleRate) {
        count(name, delta, sampleRate, null);
    }

    /**
     * Adjusts the specified counter by a given delta.
     *
     * @param name
     *     the name of the counter to adjust
     * @param delta
     *     the amount to adjust the counter by
     * @param sampleRate
     *     the sampling rate being employed. For example, a rate of 0.1 would
     *     tell StatsD that this counter is being sent
     *     sampled every 1/10th of the time.
     * @param tags
     *     Only for DogStatsD compatible collectors.
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void count(String name, long delta, double sampleRate, String tags) {
        submit(name, delta, sampleRate, "c", tags);
    }

    /**
     * Sets the specified gauge to a given value.
     *
     * @param name
     *     the name of the gauge to set
     * @param value
     *     the value to set the gauge to
     */
    public void gauge(String name, double value) {
        gauge(name, value, null);
    }

    /**
     * Sets the specified gauge to a given value.
     *
     * @param name
     *     the name of the gauge to set
     * @param value
     *     the value to set the gauge to
     * @param tags
     *     Only for DogStatsD compatible collectors.
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void gauge(String name, double value, String tags) {
        submit(name, value, 0d, "g", tags);
    }

    /**
     * Records the timing information for the specified metric.
     *
     * @param name
     *     the metric name
     * @param value
     *     the measured time
     */
    public void time(String name, long value) {
        time(name, value, null);
    }

    /**
     * Records the timing information for the specified metric.
     *
     * @param name
     *     the metric name
     * @param value
     *     the measured time
     * @param sampleRate
     *     the sampling rate being employed. For example, a rate of 0.1 would
     *     tell StatsD that this metric timing is being sent
     *     sampled every 1/10th of the time.
     */
    public void time(String name, long value, double sampleRate) {
        time(name, value, sampleRate, null);
    }

    /**
     * Records the timing information for the specified metric.
     *
     * @param name
     *     the metric name
     * @param value
     *     the measured time
     * @param tags
     *     Only for DogStatsD compatible collectors.
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void time(String name, long value, String tags) {
        time(name, value, 0d, tags);
    }

    /**
     * Records the timing information for the specified metric.
     *
     * @param name
     *     the metric name
     * @param value
     *     the measured time
     * @param sampleRate
     *     the sampling rate being employed. For example, a rate of 0.1 would
     *     tell StatsD that this metric timing is being sent
     *     sampled every 1/10th of the time.
     * @param tags
     *     Only for DogStatsD compatible collectors.
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void time(String name, long value, double sampleRate, String tags) {
        submit(name, value, sampleRate, "ms", tags);
    }

    /**
     * Adds a value to the named histogram.
     *
     * @param name
     *     the histogram name
     * @param value
     *     the measured value
     */
    public void histo(String name, double value) {
        histo(name, value, null);
    }

    /**
     * Adds a value to the named histogram.
     *
     * @param name
     *     the histogram name
     * @param value
     *     the measured value
     * @param sampleRate
     *     the sampling rate being employed. For example, a rate of 0.1 would
     *     tell StatsD that this metric value is being sent
     *     sampled every 1/10th of the time.
     */
    public void histo(String name, double value, double sampleRate) {
        histo(name, value, sampleRate, null);
    }

    /**
     * Adds a value to the named histogram.
     *
     * @param name
     *     the histogram name
     * @param value
     *     the measured value
     * @param tags
     *     Only for DogStatsD compatible collectors.
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void histo(String name, double value, String tags) {
        histo(name, value, 0d, tags);
    }

    /**
     * Adds a value to the named histogram.
     *
     * @param name
     *     the histogram name
     * @param value
     *     the measured value
     * @param sampleRate
     *     the sampling rate being employed. For example, a rate of 0.1 would
     *     tell StatsD that this metric value is being sent
     *     sampled every 1/10th of the time.
     * @param tags
     *     Only for DogStatsD compatible collectors.
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void histo(String name, double value, double sampleRate, String tags) {
        submit(name, value, sampleRate, "h", tags);
    }

    /**
     * StatsD supports counting unique occurrences of events between flushes.
     * Call this method to records an occurrence of the specified named event.
     *
     * @param name
     *     the name of the set
     * @param id
     *     the value to be added to the set
     * @param tags
     *     Only for DogStatsD compatible collectors.
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void unique(String name, String id, String tags) {
        submit(name, id, "s", tags);
    }

    /**
     * StatsD supports counting unique occurrences of events between flushes.
     * Call this method to records an occurrence of the specified named event.
     *
     * @param name
     *     the name of the set
     * @param id
     *     the value to be added to the set
     */
    public void unique(String name, String id) {
        unique(name, id, null);
    }

    /**
     * Sends an event to a DogStatsD compatible collector
     *
     * @param title event name
     * @param text event text
     */
    public void event(String title, String text) {
        event(title, text, 0, null, null, null, null, null, null);
    }

    /**
     * Sends an event to a DogStatsD compatible collector
     *
     * @param title The event name
     * @param text The event text
     * @param tags
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void event(String title, String text, String tags) {
        event(title, text, 0, null, null, null, null, null, tags);
    }

    /**
     * Sends an event to a DogStatsD compatible collector
     *
     * @param title event name
     * @param text event text
     * @param timestamp
     *     Assign a timestamp to the event.
     *     0 means the current date
     * @param host
     *     Assign a hostname to the event.
     *     May be null
     * @param group
     *     Assign an aggregation key to the event, to group it with some others.
     *     May be null
     * @param sourceType
     *     Assign a source type to the event.
     *     May be null
     * @param priority
     *     {@linkplain Priority} - may be null for NORMAL
     * @param alertType
     *     {@linkplain AlertType} - may be null for INFO
     * @param tags
     *     Assigned comma delimited tags. A tag value is delimited by colon.
     */
    public void event(String title, String text, long timestamp, String host,
                      String group, String sourceType, Priority priority,
                      AlertType alertType, String tags) {
        StringBuilder sb = new StringBuilder("_e{");
        sb.append(title.length()).append(',')
          .append(text.length()).append('}');

        sb.append(':').append(title).append('|').append(text);

        if (timestamp >= 0) {
            sb.append("|d:").append(timestamp == 0 ? System.currentTimeMillis() : timestamp);
        }
        if (host != null) {
            sb.append("|h:").append(host);
        }
        if (group != null) {
            sb.append("|k:").append(group);
        }
        if (sourceType != null) {
            sb.append("|s:").append(sourceType);
        }
        if (priority != null) {
            sb.append("|p:").append(priority);
        }
        if (alertType != null) {
            sb.append("|t:").append(alertType);
        }
        appendTags(tags, sb);

        q.offer(sb.toString());
    }

    private void submit(String name, long value, double sampleRate, String type, String tags) {
        StringBuilder sb = new StringBuilder(name);
        Formatter fmt = new Formatter(sb);

        sb.append(':').append(value).append('|').append(type);
        appendSampleRate(sampleRate, sb, fmt);
        appendTags(tags, sb);
        q.offer(sb.toString());
    }

    private void submit(String name, String value, String type, String tags) {
        StringBuilder sb = new StringBuilder(name);

        sb.append(':').append(value).append('|').append(type);
        appendTags(tags, sb);
        q.offer(sb.toString());
    }

    private void submit(String name, double value, double sampleRate, String type, String tags) {
        StringBuilder sb = new StringBuilder(name);
        Formatter fmt = new Formatter(sb);

        sb.append(':');
        fmt.format("%.3f", value);
        sb.append(value).append('|').append(type);
        appendSampleRate(sampleRate, sb, fmt);
        appendTags(tags, sb);
        q.offer(sb.toString());
    }

    private void appendTags(String tags, StringBuilder sb) {
        if (tags != null && !tags.isEmpty()) {
            sb.append("|#").append(tags);
        }
    }

    private void appendSampleRate(double sampleRate, StringBuilder sb, Formatter fmt) {
        if (sampleRate > 0) {
            sb.append('|');
            fmt.format("%.3f", sampleRate);
        }
    }
}
