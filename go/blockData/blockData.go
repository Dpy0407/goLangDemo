package blockData

import (
	. "../message"
	"encoding/binary"
	"io"
	"log"
	"os"
	"time"
)

const DATA_BASE_SEND_STEP = 128
const DATA_DEVICE_SEND_STEP = 1024
const DATA_MOBILE_SEND_STEP = 10 * 1024
const DATA_RETRY_MAX_CNT = 3 // retry 3 times
const DATA_RETRY_TIMEOUT = 5 // retry timeout 5s

const FILE_TMP_PATH = "./tmp.bin"

const (
	DATA_STATE_EMPTY = iota
	DATA_STATE_RICIEVING
	DATA_STATE_SEND_REQUIRED
	DATA_STATE_TRANS_REQ
	DATA_STATE_SENDING
	DATA_STATE_DONE = DATA_STATE_SEND_REQUIRED
)

type IBlockData struct {
	RawData    []byte
	DataLen    int
	BlockToken uint32
	LastSeq    uint32 // recieve sequence
	State      int
	SendStep   int    // send step
	SendSeq    uint32 // send sequence
	SendTime   int64  // send time, used for timeout handle
	RetryCnt   int
	lastData   []byte
}

func (this *IBlockData) Init() {
	this.RawData = this.RawData[:0]
	this.DataLen = 0
	this.BlockToken = 0
	this.LastSeq = 0
	this.State = DATA_STATE_EMPTY
	this.SendSeq = 0
	this.SendTime = -1
	this.RetryCnt = DATA_RETRY_MAX_CNT
}

func (this *IBlockData) OnReceive(data []byte) byte {
	var ret byte
	var offset uint16 = 0
	token := binary.LittleEndian.Uint32(data[:4])
	offset += 4
	seq := binary.LittleEndian.Uint32(data[offset : offset+4])
	offset += 4
	more := data[offset]
	offset += 1

	for {
		if this.State != DATA_STATE_EMPTY && this.State != DATA_STATE_RICIEVING {
			ret = MSG_INTERNAL_ERROR
			break
		}

		// this is the first sequence
		if 0 == seq {
			// do init
			this.Init()
			this.BlockToken = token
		} else {
			if token != this.BlockToken {
				log.Printf("data token error, current token = %d, received token = %d\r\n", this.BlockToken, token)
				ret = MSG_DATA_ERROR
				break
			}

		}

		// some block lost before
		if seq != 0 && this.LastSeq+1 != seq {
			log.Printf("data seq error, will be abandoned, LastSeq=%d, received seq = %d\r\n", this.LastSeq, seq)
		} else {
			this.RawData = append(this.RawData, data[offset:]...)
			this.LastSeq = seq

		}

		if 0 == more {
			this.State = DATA_STATE_SEND_REQUIRED
			this.DataLen = len(this.RawData)
			this.SendSeq = 0
			ret = MSG_DATA_ACK_DONE
		} else {
			this.State = DATA_STATE_RICIEVING
			ret = MSG_DATA_CONTINUE
		}

		break
	}

	return ret
}

func (this *IBlockData) GetSendData() []byte {
	data := make([]byte, 8)
	binary.LittleEndian.PutUint32(data[0:4], this.BlockToken)
	var start, end int
	var more uint8 = 0
	for {
		if this.DataLen < this.SendStep {
			start = 0
			end = this.DataLen
			more = 0
			break
		}

		sendCnt := int(this.SendSeq) * this.SendStep

		len := this.DataLen - sendCnt
		if len <= 0 {
			return nil
		}
		var senLen int
		if len > this.SendStep {
			senLen = this.SendStep
			more = 1
		} else {
			senLen = len
			more = 0
		}
		start = sendCnt
		end = start + senLen
		break
	}
	binary.LittleEndian.PutUint32(data[4:8], this.SendSeq)
	data = append(data, more)
	data = append(data, this.RawData[start:end]...)

	this.SendSeq += 1
	this.SendTime = time.Now().Unix()
	this.RetryCnt = DATA_RETRY_MAX_CNT
	this.lastData = data
	return data
}

// used for block resend
func (this *IBlockData) GetLastSendData() []byte {
	//data := make([]byte, 8)
	//binary.LittleEndian.PutUint32(data[0:4], this.BlockToken)
	//var start, end int
	//var more uint8 = 0
	//
	//for {
	//	if this.DataLen < this.SendStep {
	//		start = 0
	//		end = this.DataLen
	//		more = 0
	//		break
	//	}
	//
	//	start = int((this.SendSeq - 1)) * this.SendStep
	//
	//	if int(this.SendSeq)*this.SendStep >= this.DataLen {
	//		// the last block
	//		end = this.DataLen
	//		more = 0
	//	} else {
	//		end = start + this.SendStep
	//		more = 1
	//	}
	//
	//	break
	//}
	//binary.LittleEndian.PutUint32(data[4:8], this.SendSeq)
	//data = append(data, more)
	//data = append(data, this.RawData[start:end]...)
	data := this.lastData
	this.SendTime = time.Now().Unix()
	return data
}

type IFileInfo struct {
	FilePath    string
	File        *os.File
	FileSize    uint32
	LastSeq     uint32
	TranLen     uint32
	State       int
	SendSeq     uint32
	SendTime    int64 // send time, used for timeout handle
	RecieveTime int64
	RetryCnt    int
	LastData    []byte
}

func (this *IFileInfo) Init() {
	this.State = DATA_STATE_EMPTY
	this.SendSeq = 0
	this.TranLen = 0
	this.LastSeq = 0
	this.SendTime = -1
	this.RecieveTime = -1
	this.RetryCnt = DATA_RETRY_MAX_CNT
	this.LastData = this.LastData[:0]
	if this.File != nil {
		this.File.Close()
		this.File = nil
	}
}

func (this *IFileInfo) OnFileReceive(data []byte) byte {
	var ret byte
	var offset uint16 = 0
	token := binary.LittleEndian.Uint32(data[:4])
	offset += 4
	seq := binary.LittleEndian.Uint32(data[offset : offset+4])
	offset += 4
	more := data[offset]
	offset += 1

	for {
		if this.State != DATA_STATE_EMPTY && this.State != DATA_STATE_RICIEVING {
			ret = MSG_INTERNAL_ERROR
			break
		}

		if token != this.FileSize {
			log.Printf("data token error, current token = %d, received token = %d\r\n", this.FileSize, token)
			ret = MSG_DATA_ERROR
			break
		}

		// some block lost before
		if seq != 0 && this.LastSeq+1 != seq {
			log.Printf("data seq error, will be abandoned, LastSeq=%d, received seq = %d\r\n", this.LastSeq, seq)
		} else {
			_, err := this.File.Write(data[offset:])
			if err != nil {
				log.Printf("write file error, err: %v", err)
				ret = MSG_INTERNAL_ERROR
				break
			}
			this.TranLen += uint32(len(data)) - uint32(offset)
			log.Printf("writing, %d/%d, %2f\r\n", this.TranLen, this.FileSize, float32(this.TranLen)/float32(this.FileSize))
			this.RecieveTime = time.Now().Unix()
			this.LastSeq = seq
		}

		if 0 == more {
			this.State = DATA_STATE_DONE
			this.SendSeq = 0
			ret = MSG_DATA_ACK_DONE
		} else {
			this.State = DATA_STATE_RICIEVING
			ret = MSG_DATA_CONTINUE
		}

		break
	}

	return ret
}

func (this *IFileInfo) GetFileData() []byte {
	data := make([]byte, 10*1024)

	n, err := this.File.ReadAt(data, int64(this.TranLen))
	if err != nil && err != io.EOF {
		log.Printf("read file failed, %v\r\n", err)
		return nil
	}

	this.TranLen += uint32(n)

	log.Printf("uploading, %d/%d, %2f\r\n", this.TranLen, this.FileSize, float32(this.TranLen)/float32(this.FileSize))

	var more byte = 1
	if err == io.EOF || n < len(data) {
		more = 0
	}

	ret := make([]byte, 8)

	binary.LittleEndian.PutUint32(ret[0:4], this.FileSize)
	binary.LittleEndian.PutUint32(ret[4:8], this.SendSeq)
	ret = append(ret, more)
	ret = append(ret, data[0:n]...)

	this.SendSeq += 1
	this.SendTime = time.Now().Unix()
	this.RetryCnt = DATA_RETRY_MAX_CNT
	this.LastData = ret
	return ret
}

func (this *IFileInfo) GetLastFileData() []byte {
	this.SendTime = time.Now().Unix()
	return this.LastData
}
