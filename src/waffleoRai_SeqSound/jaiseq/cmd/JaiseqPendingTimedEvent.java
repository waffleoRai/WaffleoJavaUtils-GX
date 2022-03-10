package waffleoRai_SeqSound.jaiseq.cmd;

import waffleoRai_SeqSound.jaiseq.Jaiseq;
import waffleoRai_SeqSound.jaiseq.JaiseqTrack;

public abstract class JaiseqPendingTimedEvent {

	protected double change_per_tick;
	protected int remaining_ticks;
	
	protected double start;
	protected double target;
	
	protected JaiseqPendingTimedEvent(double startval, double targval, int time){
		double diff = targval - startval;
		change_per_tick = diff/(double)time;
		remaining_ticks = time;
		start = startval;
		target = targval;
	}
	
	public boolean hasRemaining(){return remaining_ticks > 0;}
	
	public void onTick(Jaiseq seq){remaining_ticks--;}
	public void onTick(JaiseqTrack track){remaining_ticks--;}
	
}
