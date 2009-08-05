/*
	Copyright 2009 Peter Hofmann

	This file is part of Multifrac.

	Multifrac is free software: you can redistribute it and/or modify it
	under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	Multifrac is distributed in the hope that it will be useful, but
	WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
	General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with Multifrac. If not, see <http://www.gnu.org/licenses/>.
*/

package multifrac.net;

import multifrac.*;

import java.net.*;
import java.io.*;
import java.awt.*;
import java.util.*;
import java.text.*;

public class Node
{
	public static final int defaultPort = 7331;

	protected FractalParameters params = null;
	protected int start, end, ID;
	protected FractalRenderer.Job job  = null;

	public static final int CMD_CLOSE   = 0;
	public static final int CMD_PING    = 1;
	public static final int CMD_ADCPUS  = 2;
	public static final int CMD_ADBUNCH = 3;

	public static final int CMD_PARAM = 1000;
	public static final int CMD_ROWS  = 1010;
	public static final int CMD_JOB   = 1100;

	/**
	 * Main node loop, receiving commands.
	 */
	public Node(int ID, Socket c, int bunch, int numthreads)
	{
		this.ID = ID;
		msg("Connected: " + c);

		try
		{
			DataInputStream din;
			DataOutputStream dout;
			DataOutputStream bout;

			din  = new DataInputStream(c.getInputStream());
			dout = new DataOutputStream(c.getOutputStream());
			bout = new DataOutputStream(
					new BufferedOutputStream(c.getOutputStream()));

			while (true)
			{
				int cmd = din.readInt();
				switch (cmd)
				{
					case CMD_CLOSE:
						msg("Closing as requested.");
						c.close();
						return;

					case CMD_PING:
						msg("PONG");
						dout.writeInt(din.readInt() + 1);
						break;

					case CMD_ADCPUS:
						msg("Advertising number of processors.");
						dout.writeInt(numthreads);
						break;

					case CMD_ADBUNCH:
						msg("Advertising bunch-size.");
						dout.writeInt(bunch);
						break;

					case CMD_PARAM:
						msg("Receiving FractalParameters and size...");
						params = new FractalParameters(din);
						int w = din.readInt();
						int h = din.readInt();
						params.updateSize(new Dimension(w, h));
						msg("Done. Current settings:" + params
								+ "\t.getWidth() : " + params.getWidth()
								+ "\n"
								+ "\t.getHeight(): " + params.getHeight()
								);
						break;

					case CMD_ROWS:
						msg("Receiving row count...");
						int rows = din.readInt();

						job = new FractalRenderer.Job(
								params,
								1,
								-1,
								null,
								rows);

						int len = job.getPixels().length;
						msg("Done. Buffer allocated: " + len + " * 4 = "
								+ (len * 4) + " Bytes");
						break;

					case CMD_JOB:
						msg("Receiving TokenSettings...");
						start = din.readInt();
						end   = din.readInt();
						msg("Okay. Rendering: " + start + ", " + end);

						FractalRenderer rend =
							new FractalRenderer(job, null);
						rend.renderPass(start, end);

						msg("Rendering done, sending image...");
						int at = 0;
						int[] px = job.getPixels();
						for (int y = start; y < end; y++)
							for (int x = 0; x < job.getWidth(); x++)
								bout.writeInt(px[at++]);
						bout.flush();
						msg("Finished this token.");
						break;

					default:
						err("Invalid command received.");
				}
			}
		}
		catch (EOFException e)
		{
			msg("Peer hung up. Thread quitting.");
		}
		catch (SocketException e)
		{
			msg("Socket gone. Thread quitting.");
			e.printStackTrace();
		}
		catch (Throwable e)
		{
			err("Unexpected error! Thread quitting.");
			e.printStackTrace();
		}
		finally
		{
			try
			{
				if (c != null)
					c.close();
			}
			catch (IOException ignore) {}
		}
	}

	public static String st()
	{
		SimpleDateFormat sdf = new SimpleDateFormat("[yyyy-MM-dd, HH:mm:ss]");
		return sdf.format(new Date());
	}

	protected void msg(String m)
	{
		System.out.println("(II) " + st() + " [" + ID + "] " + m);
	}

	protected void err(String m)
	{
		System.err.println("(EE) " + st() + " [" + ID + "] " + m);
	}

	/**
	 * Main server loop.
	 */
	public static void main(String[] args)
	{
		String host = "localhost";
		int    port = defaultPort;
		int   bunch = 10;
		int threads = Multifrac.numthreads;

		try
		{
			for (int i = 0; i < args.length; i++)
			{
				if (args[i].toUpperCase().equals("-H"))
					host = args[++i];
				else if (args[i].toUpperCase().equals("-P"))
					port = new Integer(args[++i]);
				else if (args[i].toUpperCase().equals("-B"))
					bunch = new Integer(args[++i]);
				else if (args[i].toUpperCase().equals("-T"))
					threads = new Integer(args[++i]);
				else if (args[i].toUpperCase().equals("--HELP"))
				{
					System.out.println(
							"Arguments: [-h host] [-p port] [-b bunch]"
							+ " [-t threads] [--help]");
					return;
				}
			}
		}
		catch (Exception e)
		{
			System.err.println("Could not parse arguments.");
			e.printStackTrace();
			return;
		}

		System.out.println("Rendernode starting...");

		ServerSocket s = null;
		try
		{
			final int finalbunch = bunch;
			final int finalthreads = threads;

			s = new ServerSocket(port, 0, InetAddress.getByName(host));
			System.out.println("ServerSocket up: " + s);
			System.out.println("Configured options:\n"
					+ "\tbunch   = " + finalbunch + "\n"
					+ "\tthreads = " + finalthreads);

			if (finalbunch < 1 || finalthreads < 1)
			{
				System.err.println("Those options are not useful.");
				return;
			}

			int ID = 1;
			while (true)
			{
				final Socket client = s.accept();
				final int thisID = ID++;
				Thread t = new Thread()
				{
					@Override
					public void run()
					{
						new Node(thisID, client, finalbunch, finalthreads);
					}
				};
				t.start();
			}
		}
		catch (Throwable e)
		{
			System.err.println("Fatal error! Node quitting.");
			e.printStackTrace();
			System.exit(1);
		}
		finally
		{
			try
			{
				if (s != null)
					s.close();
			}
			catch (IOException ignore) {}
		}
	}
}
