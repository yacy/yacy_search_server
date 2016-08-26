package SevenZip.Archive.SevenZip;

import java.util.Vector;

import SevenZip.Archive.Common.BindInfo;

class BindInfoEx extends BindInfo {
    
    Vector CoderMethodIDs = new Vector();
    
    public void Clear() {
        super.Clear(); // CBindInfo::Clear();
        CoderMethodIDs.clear();
    }
    
    public boolean equals(Object obj) {
    	if (obj instanceof BindInfoEx) {
    		BindInfoEx arg = (BindInfoEx)obj;
			for (int i = 0; i < this.CoderMethodIDs.size(); i++)
				if (this.CoderMethodIDs.get(i) != arg.CoderMethodIDs.get(i))
					return false;
    	}
		return super.equals(obj);
    }
}
