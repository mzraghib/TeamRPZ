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

namespace hari2
{
    public partial class Form1 : Form
    {
        public Form1()
        {
            InitializeComponent();
        }

        private void button1_Click(object sender, EventArgs e)
        {
            if (serverStarted)
            {
                updateUI("Server already started");
            }
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
            updateUI("Client has not connected");
            Stream mStream = conn.GetStream();
            while (true)
            {
                byte[] received = new byte[1024];
                mStream.Read(received, 0, received.Length);
                updateUI("Received:" + Encoding.ASCII.GetString(received));
                byte[] sent = Encoding.ASCII.GetBytes("Hello Stupid World");
                mStream.Write(sent, 0, sent.Length);
                //handle
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
}

