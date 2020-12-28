package waffleoRai_PSXMDEC;

import java.util.concurrent.ConcurrentLinkedQueue;

public class HalfwordStream {
	
	private ConcurrentLinkedQueue<Integer> queue;
	private volatile int popCount;
	
	public HalfwordStream()
	{
		queue = new ConcurrentLinkedQueue<Integer>();
		popCount = 0;
	}
	
	public void addWords(int[] blocks)
	{
		for(int i : blocks) addWord(i);
	}
	
	public void addWord(int word)
	{
		int hi = (word >>> 16) & 0xFFFF;
		int lo = word & 0xFFFF;
		queue.add(lo);
		queue.add(hi);
	}
	
	public void addHalfword(short halfword)
	{
		queue.add(Short.toUnsignedInt(halfword));
	}
	
	public void addHalfword(int halfword)
	{
		queue.add(halfword);
	}
	
	public int popHalfword()
	{
		try
		{
			popCount++; //There should only be one thread popping?
			return queue.poll();
		}
		catch(Exception e)
		{
			//System.err.println("Queue Empty :(");
			return -1;	
		}
	}
	
	public int peek(){
		try{
			return queue.peek();
		}
		catch(Exception e){
			return -1;	
		}
	}
	
	public boolean isEmpty()
	{
		return queue.isEmpty();
	}

	public int popHalfwordBlocking() throws InterruptedException
	{
		while(queue.isEmpty()) Thread.sleep(5);
		popCount++;
		return queue.poll();
	}

	public int getPopCount()
	{
		return popCount;
	}
	
	public void resetPopCount()
	{
		popCount = 0;
	}
	
	public void clear()
	{
		queue.clear();
	}
	
	
}
