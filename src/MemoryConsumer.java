/*
Author: Liran Funaro <liran.funaro@gmail.com>

Copyright (C) 2006-2018 Liran Funaro

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MemoryConsumer {
	long sleep_after_write_ms;

	int max_rand = 0;
	ArrayList<byte[]> mem_arr = new ArrayList<>();

	AtomicLong throughput = new AtomicLong(0);
	AtomicLong hits = new AtomicLong(0);
	long measureStart = System.nanoTime();
	
	public static Pattern inputRegex = Pattern.compile("(load|memory|quit|perf|resetperf|maxrand)"
			+ "(\\s*:\\s*(\\d+))?");

	public class Worker extends Thread {
		Random rand = new Random();
		AtomicBoolean running = new AtomicBoolean(true);

		@Override
		public void run() {
			while (running.get()) {
				randomWrite();
				if (running.get() && sleep_after_write_ms > 1)
					try {
						Thread.sleep(sleep_after_write_ms);
					} catch (InterruptedException e) {
					}
			}
		}

		public void randomWrite() {
			if(max_rand <= 0)
				return;
			int index = rand.nextInt(max_rand);
			throughput.incrementAndGet();

			byte[] cur;
			try {
				synchronized(mem_arr) {
					cur = mem_arr.get(index);
				}
			} catch(IndexOutOfBoundsException e) {
				return;
			}
			
			rand.nextBytes(cur);
			hits.incrementAndGet();
		}

		public void stopWorker() {
			running.set(false);
		}
	}

	ArrayList<Worker> workers = new ArrayList<>();

	public MemoryConsumer(double sleep_after_write_seconds) throws Exception {
		this.sleep_after_write_ms = (long) (sleep_after_write_seconds * 1000);
	}

	public void allocMem() {
		byte[] b = new byte[2 ^ 20];
		synchronized(mem_arr) {
			mem_arr.add(b);
		}
	}

	public void releaseMem() {
		int sz = mem_arr.size();
		if (sz == 0)
			return;
		
		synchronized(mem_arr) {
			mem_arr.remove(sz - 1);
		}
	}

	public void addLoad() {
		Worker w = new Worker();
		workers.add(w);
		w.setDaemon(true);
		w.start();
	}

	public Worker reduceLoad() {
		int sz = workers.size();
		if (sz == 0)
			return null;

		int last_ind = sz - 1;
		Worker w = workers.get(last_ind);
		w.stopWorker();
		workers.remove(last_ind);
		return w;
	}

	public void changeMem(int memTarget) {
		if (memTarget < 0)
			memTarget = 0;

		while (mem_arr.size() < memTarget)
			allocMem();
		while (mem_arr.size() > memTarget)
			releaseMem();

		Runtime.getRuntime().gc();
	}

	public void changeLoad(int loadTarget) {
		if (loadTarget < 0)
			loadTarget = 0;

		while (workers.size() < loadTarget)
			addLoad();
		ArrayList<Worker> terminated = new ArrayList<>();
		while (workers.size() > loadTarget)
			terminated.add(reduceLoad());

		for (Worker w : terminated)
			try {
				w.join();
			} catch (InterruptedException e) {}
	}
	
	public void resetperf() {
		measureStart = System.nanoTime();
		hits.set(0);
		throughput.set(0);
	}
	
	public void perf() {
		long duration_nano = System.nanoTime() - measureStart;
		double duration = duration_nano * 1e-9;
		double hits = 0;
		double throughput = 0;
		if(duration_nano > 0) {
			hits = this.hits.get() / duration;
			throughput = this.throughput.get() / duration;
		}
		
		System.out.printf("memory: %d, load: %d, hit-rate: %f, throughput: %f, duration: %f%n",
				mem_arr.size(), workers.size(), hits, throughput, duration);
	}
	
	public boolean doOp(String op, String param) throws NumberFormatException {
		switch(op) {
		case "load":
			changeLoad(Integer.valueOf(param));
			break;
		case "memory":
			changeMem(Integer.valueOf(param));
			break;
		case "perf":
			perf();
			break;
		case "resetperf":
			resetperf();
			break;
		case "maxrand":
			this.max_rand = Integer.valueOf(param);
			break;
		case "quit":
			changeLoad(0);
			changeMem(0);
			return true;
		}
		
		return false;
	}

	public static void main(String[] args) throws NumberFormatException, Exception {
		MemoryConsumer mc = null;
		try {
			mc = new MemoryConsumer(Double.valueOf(args[0]));
		} catch(NumberFormatException | ArrayIndexOutOfBoundsException e) {
			System.err.println("Parameters: <sleep after write ms (float)>");
			System.exit(1);
		} 
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String ln;
		boolean quit = false;
		while(!quit && (ln = reader.readLine()) != null) {
			Matcher m = inputRegex.matcher(ln);
			while(m.find()) {
				try {
					quit = mc.doOp(m.group(1), m.group(3));
				} catch(NumberFormatException e) {}
			}
		}
	}

}
