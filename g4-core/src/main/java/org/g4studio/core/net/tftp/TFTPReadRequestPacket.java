package org.g4studio.core.net.tftp;

import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 * A class derived from TFTPRequestPacket definiing a TFTP read request packet
 * type.
 * <p/>
 * Details regarding the TFTP protocol and the format of TFTP packets can be
 * found in RFC 783. But the point of these classes is to keep you from having
 * to worry about the internals. Additionally, only very few people should have
 * to care about any of the TFTPPacket classes or derived classes. Almost all
 * users should only be concerned with the
 * {@link org.apache.commons.net.tftp.TFTPClient} class
 * {@link org.apache.commons.net.tftp.TFTPClient#receiveFile receiveFile()} and
 * {@link org.apache.commons.net.tftp.TFTPClient#sendFile sendFile()} methods.
 * <p/>
 * <p/>
 *
 * @author Daniel F. Savarese
 * @see TFTPPacket
 * @see TFTPRequestPacket
 * @see TFTPPacketException
 * @see TFTP
 * *
 */

public final class TFTPReadRequestPacket extends TFTPRequestPacket {

    /**
     * Creates a read request packet to be sent to a host at a given port with a
     * filename and transfer mode request.
     * <p/>
     *
     * @param destination The host to which the packet is going to be sent.
     * @param port        The port to which the packet is going to be sent.
     * @param filename    The requested filename.
     * @param mode        The requested transfer mode. This should be on of the TFTP
     *                    class MODE constants (e.g., TFTP.NETASCII_MODE).
     *                    *
     */
    public TFTPReadRequestPacket(InetAddress destination, int port, String filename, int mode) {
        super(destination, port, TFTPPacket.READ_REQUEST, filename, mode);
    }

    /**
     * Creates a read request packet of based on a received datagram and assumes
     * the datagram has already been identified as a read request. Assumes the
     * datagram is at least length 4, else an ArrayIndexOutOfBoundsException may
     * be thrown.
     * <p/>
     *
     * @param datagram The datagram containing the received request.
     * @throws TFTPPacketException If the datagram isn't a valid TFTP request packet.
     *                             *
     */
    TFTPReadRequestPacket(DatagramPacket datagram) throws TFTPPacketException {
        super(TFTPPacket.READ_REQUEST, datagram);
    }

}