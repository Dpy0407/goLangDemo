package main

import (
	"encoding/binary"
	"fmt"
	"time"
)

const DATA_BASE_SEND_STEP = 128
const DATA_DEVICE_SEND_STEP = 128
const DATA_MOBILE_SEND_STEP = 512
const DATA_RETRY_MAX_CNT = 3 // retry 3 times
const DATA_RETRY_TIMEOUT = 5 // retry timeout 5s

const (
	DATA_STATE_EMPTY = iota
	DATA_STATE_RICIEVING
	DATA_STATE_SEND_REQUIRED
	DATA_STATE_SENDING
	DATA_STATE_DONE
)

type IBlockData struct {
	rawData    []byte
	dataLen    int
	blockToken uint32
	lastSeq    uint8 // recieve sequence
	state      int
	sendStep   int   // send step
	sendSeq    uint8 // send sequence
	sendTime   int64 // send time, used for timeout handle
	retryCnt   int
}

func (this *IBlockData) init() {
	this.rawData = this.rawData[:0]
	this.dataLen = 0
	this.blockToken = 0
	this.lastSeq = 0
	this.state = DATA_STATE_EMPTY
	this.sendStep = DATA_BASE_SEND_STEP
	this.sendSeq = 0
	this.sendTime = -1
	this.retryCnt = DATA_RETRY_MAX_CNT
}

func (this *IBlockData) onRevice(data []byte) byte {
	var ret byte
	token := binary.LittleEndian.Uint32(data[:4])
	seq := data[4]
	more := data[5]

	for {
		if this.state != DATA_STATE_EMPTY && this.state != DATA_STATE_RICIEVING {
			ret = MSG_INTERNAL_ERROR
			break
		}

		// this is the first sequence
		if 0 == seq {
			this.blockToken = token
		} else {
			if token != this.blockToken {
				fmt.Printf("data token error, current token = %d, received token = %d", this.blockToken, token)
				ret = MSG_DATA_ERROR
				break
			}

			// some block lost before
			if this.lastSeq+1 != seq {
				fmt.Printf("data receive sequence error, lastSeq=%d, received seq=%d", this.lastSeq, seq)
				ret = MSG_DATA_ERROR
				break
			}
		}

		this.rawData = append(this.rawData, data[6:]...)
		this.lastSeq = seq

		if 0 == more {
			this.state = DATA_STATE_SEND_REQUIRED
			this.dataLen = len(this.rawData)
			this.sendSeq = 0
			ret = MSG_ACK
		} else {
			this.state = DATA_STATE_RICIEVING
			ret = MSG_DATA_CONTINUE
		}

		break
	}

	return ret
}

func (this *IBlockData) getSendData() []byte {
	var data []byte
	binary.LittleEndian.PutUint32(data, this.blockToken)
	var start, end int
	var more uint8 = 0
	for {
		if this.dataLen < this.sendStep {
			start = 0
			end = this.dataLen
			more = 0
			break
		}

		sendCnt := int(this.sendSeq) * this.sendStep

		len := this.dataLen - sendCnt
		if len < 0 {
			return nil
		}
		var senLen int
		if len > this.sendStep {
			senLen = this.sendStep
			more = 1
		} else {
			senLen = len
			more = 0
		}
		start = sendCnt
		end = start + senLen
		break
	}

	data = append(data, this.sendSeq)
	data = append(data, more)
	data = append(data, this.rawData[start:end]...)

	this.sendSeq += 1
	this.sendTime = time.Now().Unix()
	this.retryCnt = DATA_RETRY_MAX_CNT
	return data
}

// used for block resend
func (this *IBlockData) getLastSendData() []byte {
	var data []byte
	binary.LittleEndian.PutUint32(data, this.blockToken)
	var start, end int
	var more uint8 = 0

	for {
		if this.dataLen < this.sendStep {
			start = 0
			end = this.dataLen
			more = 0
			break
		}

		start = int((this.sendSeq - 1)) * this.sendStep

		if int(this.sendSeq)*this.sendStep >= this.dataLen {
			// the last block
			end = this.dataLen
			more = 0
		} else {
			end = start + this.sendStep
			more = 1
		}

		break
	}

	data = append(data, this.sendSeq)
	data = append(data, more)
	data = append(data, this.rawData[start:end]...)
	this.sendTime = time.Now().Unix()
	return data
}
