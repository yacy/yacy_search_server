package SevenZip.Archive.SevenZip;

import java.io.IOException;
import java.util.Vector;

class StreamSwitch {
	
    InStream _archive;
    boolean _needRemove;

    public StreamSwitch() {
        _needRemove = false;
    }
    
    public void close() {
        Remove();
    }
    
    void Remove() {
        if (_needRemove) {
            _archive.DeleteByteStream();
            _needRemove = false;
        }
    }
    
    void Set(InStream archive, byte[] data) {
    	Set(archive, data, data.length);
    }
    
    void Set(InStream archive, byte[] data, int size) {
        Remove();
        _archive = archive;
        _archive.AddByteStream(data, size);
        _needRemove = true;
    }
    
    void Set(InStream archive, Vector dataVector) throws IOException {
        Remove();
        int external = archive.ReadByte();
        if (external != 0) {
            int dataIndex = archive.ReadNum();
            Set(archive, (byte[])dataVector.get(dataIndex));
        }
    }
}
