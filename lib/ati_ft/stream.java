import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import wirelessftsensor.WirelessFTSample;
import wirelessftsensor.crc;


class stream
{
    /**
     * The socket that sends and receives each data sample.
     */
    public static DatagramSocket m_udpSocket = null;
    /**
     * Used to synchronize access to m_bufferedWriter.
     */
    private final Object m_bufferedWriterSynchroLock = new Object();
    /**
     * Writes collected data to file.
     */
    private BufferedWriter m_bufferedWriter = null;
    public static final int NUM_SENSORS = 3; 

    public static final int runtime = 100; /*seconds to run */
    public static final int i = runtime*500; /*datapackets*/

    public static void main(String[] args ) throws UnknownHostException, SecurityException, IOException 
    {   
        final int UDP_SERVER_PORT = 49152;
        InetAddress sensorAddress = InetAddress.getByName("192.168.1.107"); /*Device IP - May Change*/
        int numSamples = 0;
        m_udpSocket = new DatagramSocket(0, InetAddress.getByName("0.0.0.0"));
        final short LENGTH = 10;                     /* Length of this command. */
        byte[] packetData = new byte[LENGTH];        /* The command data. */
        ByteBuffer bb = ByteBuffer.wrap(packetData); /* Places fields in data. */
        bb.putShort(LENGTH);                         /* Length. */
        bb.put((byte) 0);                            /* Sequence number / unused. */
        bb.put((byte) 1);                            /* Command.  1 = start streaming. */
        bb.putInt(numSamples);                       /* Number of samples. */
        bb.putShort(crc.crcBuf(packetData, LENGTH - 2));
        m_udpSocket.send(new DatagramPacket(packetData, LENGTH, sensorAddress, UDP_SERVER_PORT));

        stream listen = new stream();
        listen.listen();
    }

    public void listen()throws UnknownHostException, SecurityException, IOException 
    {           
        /**
        * Set file to save data
        */
        String filename = "ft.csv";
        filename = filename.substring(0, filename.length() - 4)
                    + "-" + Calendar.getInstance().getTime().toString().replaceAll("[ :]", "-")
                    + filename.substring(filename.length() - 4);
        startCollectingData(filename);
    }
    public void startCollectingData(String filePath) throws IllegalStateException, IOException 
    {   
        System.out.println("Start data stream");
        synchronized (m_bufferedWriterSynchroLock)        {
            m_bufferedWriter = Files.newBufferedWriter(Paths.get(filePath), Charset.forName("US-ASCII"));
            /* Write column headers. */
            m_bufferedWriter.write("Time, Mask, Bat, Sts1, Sts2, Seq #");
            String[] channelNames = new String[]{"FX", "FY", "FZ", "TX", "TY", "TZ"};
            
            for (int transducer = 0; transducer < 3; transducer++) 
            {
                for (int axis = 0; axis < 6; axis++)
                {
                    m_bufferedWriter.write(",T" + Integer.toString(transducer + 1)
                            + channelNames[axis]);
                }
            }
            m_bufferedWriter.write("\n");
        }
        new Thread(new CollectDataThread()).start(); // Start collecting records from the WNet.
    }
     public ArrayList<WirelessFTSample> readStreamingSamples() throws IOException 
    {           
        DatagramPacket receivedData = new DatagramPacket(new byte[1024], 1024); // Make a new empty UDP packet
        m_udpSocket.receive(receivedData);                                                                    // Get UDP packet from Wnet
        return WirelessFTSample.listOfSamplesFromPacket(receivedData.getData(), receivedData.getLength());    // Split UDP packet into indivual samples
    } 

    private class CollectDataThread implements Runnable 
    {   
        /**
         * Begin reading records.
         * If the time between records
         * exceeds the 1-minute timeout threshold, attempt to
         * handle a disconnect scenario.
         * If the file-write flag is set, also record each of
         * these records to the user-specified .csv or .txt file.
         */
        public void run() 
        {
            int a = 0;
            while (a < i) 
            {
                try 
                {
                    ArrayList<WirelessFTSample> samples = readStreamingSamples(); // Get all packets from next UDP data block
                    
                    for (WirelessFTSample s : samples)    // For each sample block in the UDP data block,
                    {
                        synchronized (m_bufferedWriterSynchroLock) 
                        {
                            WriteDataBlockToFile(s);
                        }
                    }
                } 
                catch (IOException exc)  // UDP socket timeout.
                {
                    }
                    a++;
                }
            stopCollectingData();
        }
    }
    
    private void WriteDataBlockToFile(WirelessFTSample s) throws IOException
    {   
        try
        {
        m_bufferedWriter.write(String.format("%d, %2x, %d, %8x, %8x, %d",
                         s.getTimeStamp(), 
                         s.getSensorMask(),
                         (int) s.getBatteryLevel(),
                         s.getStatusCode1(), 
                         s.getStatusCode2(),
                         s.getSequence())); 
        } 
        catch (IOException A) 
        {}
                                
        for (int transducer = 0; transducer < 3; transducer++) // For each Transducer,
        {
            int[] data = s.getFtOrGageData()[transducer];
                                    
            for (int axis = 0; axis < 6; axis++)              // For each channel,
            {
                double value = data[axis];                                   // get the data value.                                          
                                           
                m_bufferedWriter.write(", " + Double.toString(value));       // Save converted value to .csv file.
            }
        }
        m_bufferedWriter.write("\n");
    }
        private double[][][] matrixMult(double[][][] r, double[][][] d, boolean[] validR, boolean[] validD) {
        if(r.length != d.length) return null; //invalid dims
        
        double ans[][][] = new double[NUM_SENSORS][NUM_SENSORS][NUM_SENSORS];

        for (int x = 0; x < NUM_SENSORS; x++) 
        {// 6 Matrices.
            // Only do math for valid transformations.
            if (validR[x] || validD[x]) 
            {
                for (int i = 0; i < NUM_SENSORS; i++)  
                { // 6 Rows each
                    for (int j = 0; j < NUM_SENSORS; j++) 
                    { // 6 Cols in d
                        for (int k = 0; k < NUM_SENSORS; k++) 
                        { // 6 Cols in r
                           ans[x][i][j] += r[x][i][k] * d[x][k][j];
                        }
                    }
                }
            } 
            else 
            { // Don't change this transducer (use the identity matrix).
                for (int i = 0; i < NUM_SENSORS; i++)
                {
                    ans[x][i][i] = 1.0;
                }
            }
        }
        
        return ans;
    }
    /**
     * Closes the file to which data is being
     * collected and stops writing data.
     */
    public void stopCollectingData() 
    {   
        if (m_bufferedWriter != null) 
        {
            synchronized (m_bufferedWriterSynchroLock) 
            {
                try 
                {
                    m_bufferedWriter.close();
                    System.out.println("Stop data stream");
                } 
                catch (IOException e) 
                {
                }
            }
        }
    }
    

    static public short crcByte(short crc, byte ch) // lookup table version (bigger & faster)
    {
        int[] ccitt_crc16_table = 
        {
            0x0000, 0x1021, 0x2042, 0x3063, 0x4084, 0x50a5, 0x60c6, 0x70e7,
            0x8108, 0x9129, 0xa14a, 0xb16b, 0xc18c, 0xd1ad, 0xe1ce, 0xf1ef,
            0x1231, 0x0210, 0x3273, 0x2252, 0x52b5, 0x4294, 0x72f7, 0x62d6,
            0x9339, 0x8318, 0xb37b, 0xa35a, 0xd3bd, 0xc39c, 0xf3ff, 0xe3de,
            0x2462, 0x3443, 0x0420, 0x1401, 0x64e6, 0x74c7, 0x44a4, 0x5485,
            0xa56a, 0xb54b, 0x8528, 0x9509, 0xe5ee, 0xf5cf, 0xc5ac, 0xd58d,
            0x3653, 0x2672, 0x1611, 0x0630, 0x76d7, 0x66f6, 0x5695, 0x46b4,
            0xb75b, 0xa77a, 0x9719, 0x8738, 0xf7df, 0xe7fe, 0xd79d, 0xc7bc,
            0x48c4, 0x58e5, 0x6886, 0x78a7, 0x0840, 0x1861, 0x2802, 0x3823,
            0xc9cc, 0xd9ed, 0xe98e, 0xf9af, 0x8948, 0x9969, 0xa90a, 0xb92b,
            0x5af5, 0x4ad4, 0x7ab7, 0x6a96, 0x1a71, 0x0a50, 0x3a33, 0x2a12,
            0xdbfd, 0xcbdc, 0xfbbf, 0xeb9e, 0x9b79, 0x8b58, 0xbb3b, 0xab1a,
            0x6ca6, 0x7c87, 0x4ce4, 0x5cc5, 0x2c22, 0x3c03, 0x0c60, 0x1c41,
            0xedae, 0xfd8f, 0xcdec, 0xddcd, 0xad2a, 0xbd0b, 0x8d68, 0x9d49,
            0x7e97, 0x6eb6, 0x5ed5, 0x4ef4, 0x3e13, 0x2e32, 0x1e51, 0x0e70,
            0xff9f, 0xefbe, 0xdfdd, 0xcffc, 0xbf1b, 0xaf3a, 0x9f59, 0x8f78,
            0x9188, 0x81a9, 0xb1ca, 0xa1eb, 0xd10c, 0xc12d, 0xf14e, 0xe16f,
            0x1080, 0x00a1, 0x30c2, 0x20e3, 0x5004, 0x4025, 0x7046, 0x6067,
            0x83b9, 0x9398, 0xa3fb, 0xb3da, 0xc33d, 0xd31c, 0xe37f, 0xf35e,
            0x02b1, 0x1290, 0x22f3, 0x32d2, 0x4235, 0x5214, 0x6277, 0x7256,
            0xb5ea, 0xa5cb, 0x95a8, 0x8589, 0xf56e, 0xe54f, 0xd52c, 0xc50d,
            0x34e2, 0x24c3, 0x14a0, 0x0481, 0x7466, 0x6447, 0x5424, 0x4405,
            0xa7db, 0xb7fa, 0x8799, 0x97b8, 0xe75f, 0xf77e, 0xc71d, 0xd73c,
            0x26d3, 0x36f2, 0x0691, 0x16b0, 0x6657, 0x7676, 0x4615, 0x5634,
            0xd94c, 0xc96d, 0xf90e, 0xe92f, 0x99c8, 0x89e9, 0xb98a, 0xa9ab,
            0x5844, 0x4865, 0x7806, 0x6827, 0x18c0, 0x08e1, 0x3882, 0x28a3,
            0xcb7d, 0xdb5c, 0xeb3f, 0xfb1e, 0x8bf9, 0x9bd8, 0xabbb, 0xbb9a,
            0x4a75, 0x5a54, 0x6a37, 0x7a16, 0x0af1, 0x1ad0, 0x2ab3, 0x3a92,
            0xfd2e, 0xed0f, 0xdd6c, 0xcd4d, 0xbdaa, 0xad8b, 0x9de8, 0x8dc9,
            0x7c26, 0x6c07, 0x5c64, 0x4c45, 0x3ca2, 0x2c83, 0x1ce0, 0x0cc1,
            0xef1f, 0xff3e, 0xcf5d, 0xdf7c, 0xaf9b, 0xbfba, 0x8fd9, 0x9ff8,
            0x6e17, 0x7e36, 0x4e55, 0x5e74, 0x2e93, 0x3eb2, 0x0ed1, 0x1ef0
        };

        return (short) (ccitt_crc16_table[((crc >> 8) ^ ch) & 0xff] ^ (crc << 8));
    }

    static public short crcBuf(byte[] buff, int len) 
    {
        int i;
        short crc = 0x1234; // CRC seed.
        
        for (i = 0; i < len; i++) 
        {
            crc = crcByte(crc, buff[i]);
        }

        return crc;
    }
    
}