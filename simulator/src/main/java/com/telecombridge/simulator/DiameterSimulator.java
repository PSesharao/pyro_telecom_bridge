package com.telecombridge.simulator;

import com.telecombridge.codec.MessageFrameDecoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone Diameter server simulator for development and testing.
 * <p>
 * Listens on a configurable TCP port (default 3868) and responds to
 * CER, CCR, and DWR messages. Unrecognized command codes are discarded
 * with a WARN log.
 */
public class DiameterSimulator {

    private static final Logger log = LoggerFactory.getLogger(DiameterSimulator.class);

    private static final int DEFAULT_PORT = 3868;
    private static final int DEFAULT_DELAY_MS = 50;
    private static final int DEFAULT_WORKER_THREADS = 4;

    private final int port;
    private final int delayMs;
    private final int workerThreads;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /**
     * Creates a new DiameterSimulator with the specified configuration.
     *
     * @param port          the TCP port to listen on
     * @param delayMs       the simulated response delay in milliseconds for CCR
     * @param workerThreads the number of Netty worker threads
     */
    public DiameterSimulator(int port, int delayMs, int workerThreads) {
        this.port = port;
        this.delayMs = Math.min(delayMs, 100); // max 100ms
        this.workerThreads = workerThreads;
    }

    /**
     * Starts the simulator, binding to the configured port.
     *
     * @throws InterruptedException if the bind operation is interrupted
     */
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(workerThreads);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                new MessageFrameDecoder(),
                                new SimulatorHandler(delayMs)
                        );
                    }
                });

        serverChannel = bootstrap.bind(port).sync().channel();
        log.info("Diameter Simulator started on port {} with delay={}ms, workers={}",
                port, delayMs, workerThreads);
    }

    /**
     * Stops the simulator gracefully, releasing all resources.
     */
    public void stop() {
        log.info("Shutting down Diameter Simulator...");
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Diameter Simulator stopped.");
    }

    /**
     * Main entry point for the Diameter Simulator.
     * <p>
     * Accepts configuration via command-line arguments or system properties:
     * <ul>
     *   <li>{@code --port=<port>} or {@code -Dsimulator.port=<port>} (default: 3868)</li>
     *   <li>{@code --delay=<ms>} or {@code -Dsimulator.delay=<ms>} (default: 50, max: 100)</li>
     *   <li>{@code --workers=<n>} or {@code -Dsimulator.workers=<n>} (default: 4)</li>
     * </ul>
     */
    public static void main(String[] args) throws InterruptedException {
        int port = getIntConfig(args, "port", "simulator.port", DEFAULT_PORT);
        int delay = getIntConfig(args, "delay", "simulator.delay", DEFAULT_DELAY_MS);
        int workers = getIntConfig(args, "workers", "simulator.workers", DEFAULT_WORKER_THREADS);

        DiameterSimulator simulator = new DiameterSimulator(port, delay, workers);

        // Register shutdown hook for graceful termination on SIGTERM
        Runtime.getRuntime().addShutdownHook(new Thread(simulator::stop, "simulator-shutdown"));

        simulator.start();

        // Block until the server channel is closed
        simulator.serverChannel.closeFuture().sync();
    }

    /**
     * Resolves an integer configuration value from command-line args, system properties,
     * or falls back to the default.
     */
    private static int getIntConfig(String[] args, String argName, String sysProp, int defaultValue) {
        // Check command-line arguments (--name=value)
        String prefix = "--" + argName + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                try {
                    return Integer.parseInt(arg.substring(prefix.length()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid command-line argument '{}', using default {}", arg, defaultValue);
                }
            }
        }

        // Check system property
        String sysPropValue = System.getProperty(sysProp);
        if (sysPropValue != null) {
            try {
                return Integer.parseInt(sysPropValue);
            } catch (NumberFormatException e) {
                log.warn("Invalid system property '{}={}', using default {}", sysProp, sysPropValue, defaultValue);
            }
        }

        return defaultValue;
    }
}
