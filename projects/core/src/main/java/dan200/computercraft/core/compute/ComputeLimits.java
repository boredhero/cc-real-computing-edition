// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.compute;

/**
 * Resource limits for a server's independent compute pool.
 *
 * @param threads       The maximum number of concurrent compute threads.
 * @param globalJobs    The maximum outstanding jobs across the pool, including retained results.
 * @param computerJobs  The maximum outstanding jobs per computer boot.
 * @param payloadBytes  The maximum encoded input or result size. This is not a working heap limit.
 * @param sourceBytes   The maximum source size per job.
 * @param runtimeMillis The maximum accumulated execution time per job, excluding time in the queue.
 */
public record ComputeLimits(int threads, int globalJobs, int computerJobs, int payloadBytes, int sourceBytes, int runtimeMillis) {
    public static final ComputeLimits DEFAULT = new ComputeLimits(2, 256, 8, 1024 * 1024, 256 * 1024, 300_000);

    public ComputeLimits {
        if (threads < 1 || globalJobs < 1 || computerJobs < 1 || payloadBytes < 1 || sourceBytes < 1 || runtimeMillis < 1) {
            throw new IllegalArgumentException("Compute limits must be positive");
        }
    }
}
