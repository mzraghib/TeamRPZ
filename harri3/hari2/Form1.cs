using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;
using System.Threading;
using InTheHand;
using InTheHand.Net.Bluetooth;
using InTheHand.Net.Ports;
using InTheHand.Net.Sockets;
using System.IO;
using System.Runtime.InteropServices;
using System.Runtime.Serialization.Formatters.Binary;

namespace hari2
{
    public partial class Form1 : Form
    {
        public int x = 0;
        public int y = 0;
        public int z = 0;
        public int numbytes = 0;
      

        public Form1()
        {
            InitializeComponent();
           // VirtualMouse.Move(x,y);

        }
        Rectangle rectscreen;
        public static Cursor Arrow { get; }


        private void button1_Click(object sender, EventArgs e)
        {
            if (serverStarted)
            {
                updateUI("Server already started");
            }
           
//            byte[] received = new byte[] { 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20 };
           // x = BitConverter.ToInt32(received, 0);
           // y = BitConverter.ToInt32(received, 0);
  //          VirtualMouse.Move(x, y);
            connectAsServer();
            //textBox1.AppendText(message + System.Environment.NewLine);
           
        }
    
        private void connectAsServer()
        {
            //message = "Server started, waiting for client";
            //textBox1.AppendText(message + System.Environment.NewLine);
            Thread bluetoothServerThread = new Thread(new ThreadStart(ServerConnectThread));
            bluetoothServerThread.Start();
        }

        //Guid mUUID = Guid.NewGuid();


        Guid mUUID = new Guid("00001101-0000-1000-8000-00805F9B34FB");
        bool serverStarted = false;

        
        public void ServerConnectThread()
        {
            
            serverStarted = true;
            updateUI("Server started and phone andriod connected ");
            //message = "Server started, waiting for client";
            BluetoothListener blueListener = new BluetoothListener(mUUID);
            blueListener.Start();
            BluetoothClient conn = blueListener.AcceptBluetoothClient();
            //  message = "client has connected";
            //    textBox1.AppendText(message + System.Environment.NewLine);
            updateUI("Client has connected");
            Stream mStream = conn.GetStream();
            Rectangle rect = new Rectangle(int.MaxValue, int.MaxValue, int.MinValue, int.MinValue);
            foreach (Screen screen in Screen.AllScreens)
                rect = Rectangle.Union(rect, screen.Bounds);
            updateUI("width:" + rect.Width.ToString());
            updateUI("hight:" + rect.Height.ToString());
            while (true)
            {
                try
                {

                    byte[] received = new byte[1024];
                    numbytes++;
                    
             //       updateUI(getRemoteCommand(received));
                    mStream.Read(received, 0, received.Length);
                    byte[] received2 = new byte[] { 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20 };
                   if (numbytes == 1)
                    {
                        x = ((BitConverter.ToInt32(received, 0) - 1040000000) / 2000000);
                        }
                            if (numbytes == 2)
                           {
                            y = ((BitConverter.ToInt32(received, 0) - 1040000000) / 2000000);
                            numbytes = 0;
                         }

                  
                    VirtualMouse.Move(Math.Min(x,rect.Width), Math.Min(y , rect.Height));
                    updateUI("Received:" + ((BitConverter.ToInt32(received, 0)-1040000000)/200000));
                    //updateUI("Received:" + Encoding.ASCII.GetString(received));
                    //updateUI("Received:" + (received));
                    byte[] sent = Encoding.ASCII.GetBytes("Hello Stupid World");
                    mStream.Write(sent, 0, sent.Length);
                }
                catch(IOException exception)
                {
                    updateUI("client dissconected");
                }

          }

        }
        private void updateUI(string message)
        {
            Func<int> del = delegate ()
            {
                textBox1.AppendText(message + System.Environment.NewLine);
                return 0;
            };
            Invoke(del);
        }
    }
    public static class VirtualMouse
    {
        [DllImport("user32.dll")]
        static extern void mouse_event(int dwFlags, int dx, int dy, int dwData, int dwExtraInfo);
        private const int MOUSEEVENTF_MOVE = 0x0001;
        private const int MOUSEEVENTF_LEFTDOWN = 0x0002;
        private const int MOUSEEVENTF_LEFTUP = 0x0004;
        private const int MOUSEEVENTF_RIGHTDOWN = 0x0008;
        private const int MOUSEEVENTF_RIGHTUP = 0x0010;
        private const int MOUSEEVENTF_MIDDLEDOWN = 0x0020;
        private const int MOUSEEVENTF_MIDDLEUP = 0x0040;
        private const int MOUSEEVENTF_ABSOLUTE = 0x8000;
        public static void Move(int xDelta, int yDelta)
        {
            mouse_event(MOUSEEVENTF_MOVE,  xDelta, yDelta, 0, 0);
        }
        public static void MoveTo(int x, int y)
        {
            mouse_event(MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_MOVE, x, y, 0, 0);
        }
        public static void LeftClick()
        {
            mouse_event(MOUSEEVENTF_LEFTDOWN, System.Windows.Forms.Control.MousePosition.X, System.Windows.Forms.Control.MousePosition.Y, 0, 0);
            mouse_event(MOUSEEVENTF_LEFTUP, System.Windows.Forms.Control.MousePosition.X, System.Windows.Forms.Control.MousePosition.Y, 0, 0);
        }

        public static void LeftDown()
        {
            mouse_event(MOUSEEVENTF_LEFTDOWN, System.Windows.Forms.Control.MousePosition.X, System.Windows.Forms.Control.MousePosition.Y, 0, 0);
        }

        public static void LeftUp()
        {
            mouse_event(MOUSEEVENTF_LEFTUP, System.Windows.Forms.Control.MousePosition.X, System.Windows.Forms.Control.MousePosition.Y, 0, 0);
        }

        public static void RightClick()
        {
            mouse_event(MOUSEEVENTF_RIGHTDOWN, System.Windows.Forms.Control.MousePosition.X, System.Windows.Forms.Control.MousePosition.Y, 0, 0);
            mouse_event(MOUSEEVENTF_RIGHTUP, System.Windows.Forms.Control.MousePosition.X, System.Windows.Forms.Control.MousePosition.Y, 0, 0);
        }

        public static void RightDown()
        {
            mouse_event(MOUSEEVENTF_RIGHTDOWN, System.Windows.Forms.Control.MousePosition.X, System.Windows.Forms.Control.MousePosition.Y, 0, 0);
        }

        public static void RightUp()
        {
            mouse_event(MOUSEEVENTF_RIGHTUP, System.Windows.Forms.Control.MousePosition.X, System.Windows.Forms.Control.MousePosition.Y, 0, 0);
        }
    }
}

