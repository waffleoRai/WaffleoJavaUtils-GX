package waffleoRai_SeqSound.jaiseq.cmd;

import waffleoRai_SeqSound.jaiseq.Jaiseq;
import waffleoRai_SeqSound.jaiseq.JaiseqCommand;
import waffleoRai_SeqSound.jaiseq.JaiseqTrack;

public class DummyJaiseqCommand extends JaiseqCommand{

	public DummyJaiseqCommand(EJaiseqCmd c_enum) {super(c_enum);}

	public boolean doAction(Jaiseq seq) {return true;}
	public boolean doAction(JaiseqTrack track) {return false;}

}
