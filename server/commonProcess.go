package main

import (
	. "../blockData"
	. "../message"
	"encoding/binary"
	"log"
	"os"
	"time"
)

func onMsgReceived(ctx *IContex, msg IMessage) {
	switch msg.MsgType {
	case MSG_POST_DATA:
		onDataReceived(ctx, msg)
		break
	case MSG_DATA_CONTINUE:
		onDataContinue(ctx, msg)
		break
	case MSG_INTERNAL_ERROR:
		onDataError(ctx, msg, false)
		break
	case MSG_DATA_ERROR:
		onDataError(ctx, msg, true)
		break
	case MSG_DATA_ACK_DONE:
		onDataAckDone(ctx, msg)
		break
	case MSG_TRANS_START:
		onTransStart(ctx, msg)
		break
	case MSG_TRANS_DATA:
		onTransData(ctx, msg)
		break
	default:
		break
	}
}

func onDataReceived(ctx *IContex, msg IMessage) {
	var resp IMessage
	var ret byte

	if DEVICE == msg.MsgSrc {
		ret = ctx.device2mobileData.OnReceive(msg.Payload)
	} else if MOBILE == msg.MsgSrc {
		ret = ctx.mobile2deviceData.OnReceive(msg.Payload)
	}

	resp.MsgSrc = SERVER
	resp.MsgId = msg.MsgId
	resp.MsgType = ret
	resp.Payload = nil
	resp.MsgDst = msg.MsgSrc

	ctx.sendMessage(resp)
}

func onDataContinue(ctx *IContex, msg IMessage) {
	var blockData *IBlockData
	if DEVICE == msg.MsgSrc {
		blockData = &ctx.mobile2deviceData
	} else if MOBILE == msg.MsgSrc {
		blockData = &ctx.device2mobileData
	}

	if DATA_STATE_SENDING != blockData.State {
		log.Printf("invalid request!\r\n")
		return
	}

	data := blockData.GetSendData()

	if data == nil {
		log.Printf("data is empty, request from 0x%X\r\n", msg.MsgSrc)
		blockData.Init()
		return
	}

	dataSend(ctx, msg.MsgSrc, data)
}

func onDataError(ctx *IContex, msg IMessage, retry bool) {
	var blockData *IBlockData
	if DEVICE == msg.MsgSrc {
		blockData = &ctx.mobile2deviceData
	} else if MOBILE == msg.MsgSrc {
		blockData = &ctx.device2mobileData
	}

	if retry && blockData.RetryCnt > 0 {
		// try send again
		if DATA_STATE_SENDING == blockData.State {
			log.Printf("error ack, retry data sending, retryCnt = %d\r\n", blockData.RetryCnt)
			data := blockData.GetLastSendData()
			dataSend(ctx, msg.MsgSrc, data)
			blockData.RetryCnt -= 1
		}
	} else {
		blockData.Init()
	}

}

func onDataAckDone(ctx *IContex, msg IMessage) {
	var blockData *IBlockData
	if DEVICE == msg.MsgSrc {
		blockData = &ctx.mobile2deviceData
	} else if MOBILE == msg.MsgSrc {
		blockData = &ctx.device2mobileData
	}

	//log.Printf("get MSG_DATA_ACK_DONE from 0x%X\r\n", msg.MsgSrc)
	// Init block data, prepare for next receive & send
	blockData.Init()
}

func dataSend(ctx *IContex, dst byte, data []byte) {
	if data == nil {
		log.Printf("data is empty\r\n")
		return
	}

	if dst != DEVICE && dst != MOBILE {
		log.Printf("invalid dst, dst = 0x%X", dst)
		return
	}

	var resp IMessage
	resp.MsgSrc = SERVER
	resp.MsgDst = dst
	resp.MsgType = MSG_PUT_DATA
	ctx.MsgIdMutex.Lock()
	resp.MsgId = ctx.MsgId
	ctx.MsgId += 1
	ctx.MsgIdMutex.Unlock()

	resp.Payload = data
	ctx.sendMessage(resp)
}

func baseStep(ctx *IContex, src byte) {
	var blockData *IBlockData = nil
	var dst byte
	var src_s, dst_s string
	if DEVICE == src {
		blockData = &ctx.device2mobileData
		dst = MOBILE
		src_s = "device"
		dst_s = "mobile"
	} else if MOBILE == src {
		blockData = &ctx.mobile2deviceData
		dst = DEVICE
		src_s = "mobile"
		dst_s = "device"
	}

	if blockData == nil {
		return
	}

	if DATA_STATE_SEND_REQUIRED == blockData.State {
		log.Printf("%s -> %s, sending start, data token = 0x%08X\r\n", src_s, dst_s, blockData.BlockToken)
		data := blockData.GetSendData()
		dataSend(ctx, dst, data)
		blockData.State = DATA_STATE_SENDING
	} else if DATA_STATE_SENDING == blockData.State {
		if time.Now().Unix()-blockData.SendTime > DATA_RETRY_TIMEOUT {
			if blockData.RetryCnt > 0 {
				data := blockData.GetLastSendData()
				log.Printf("seq [%d] timeout, retry data sending, retryCnt = %d\r\n", data[4], blockData.RetryCnt)
				dataSend(ctx, dst, data)
				blockData.RetryCnt -= 1
			} else {
				// give up current data, init dataBlock
				log.Printf("send failed.\r\n")
				blockData.Init()
			}
		}
	}
}

func onTransStart(ctx *IContex, msg IMessage) {
	var resp IMessage

	ctx.fileInfo.Init()
	data := msg.Payload
	ctx.fileInfo.FileSize = binary.LittleEndian.Uint32(data[0:4])
	ctx.fileInfo.FilePath = "./" + string(data[4:])

	if _, err := os.Stat(FILE_TMP_PATH); err == nil {
		log.Printf("remove old file...\r\n")
		os.Remove(FILE_TMP_PATH)
	}

	fileObj, err := os.OpenFile(FILE_TMP_PATH, os.O_RDWR|os.O_APPEND|os.O_CREATE, 0644)
	if err != nil {
		log.Printf("err：%v", err)
		log.Printf("open file failed, filePath: %s\r\n", ctx.fileInfo.FilePath)
		ctx.fileTransMode = false
		resp.MsgType = MSG_DATA_ERROR
	} else {
		ctx.fileTransMode = true
		resp.MsgType = MSG_TRANS_ACK
		ctx.fileInfo.File = fileObj
	}

	resp.MsgSrc = SERVER
	resp.MsgId = msg.MsgId
	resp.Payload = nil
	resp.MsgDst = msg.MsgSrc

	ctx.sendMessage(resp)
}

func onTransData(ctx *IContex, msg IMessage) {
	var resp IMessage
	var ret byte

	ret = ctx.fileInfo.OnFileReceive(msg.Payload)
	resp.MsgSrc = SERVER
	resp.MsgId = msg.MsgId
	resp.MsgType = ret
	resp.Payload = nil
	resp.MsgDst = msg.MsgSrc

	ctx.sendMessage(resp)
}
