package main

import (
	. "../blockData"
	. "../message"
	"encoding/binary"
	"log"
	"os"
	"path"
	"strings"
	"time"
)

func onProcess(ctx *IClientContex) {

	for true {
		select {
		case msg := <-ctx.msgChan:
			onMsgRecieved(ctx, msg)
			break
		case cmd := <-ctx.cmdChan:
			onCommand(ctx, cmd)
			break
		default:
			onStep(ctx)
		}
		time.Sleep(1 * time.Microsecond)
	}
}

func onMsgRecieved(ctx *IClientContex, msg *IMessage) {
	switch msg.MsgType {
	case MSG_PUT_DATA:
		onDataReceived(ctx, *msg)
		break
	case MSG_DATA_CONTINUE:
		if ctx.fileTranMode {
			onTransContinue(ctx, *msg)
		} else {
			onDataContinue(ctx, *msg)
		}
		break
	case MSG_DATA_ERROR:
		if ctx.fileTranMode {
			onTransError(ctx, *msg)
		} else {
			onDataError(ctx, *msg)
		}
		break
	case MSG_DATA_ACK_DONE:
		if ctx.fileTranMode {
			onTransAckDone(ctx, *msg)
		} else {
			onDataAckDone(ctx, *msg)
		}
		break
	case MSG_TRANS_ACK:
		onTransAck(ctx, *msg)
		break
	default:
		break
	}
}

func onCommand(ctx *IClientContex, cmd string) {
	cmd = strings.ToLower(cmd)
	if cmd == "send" || cmd == "s" {
		if DATA_STATE_SENDING == ctx.sendData.State {
			log.Println("busy on sending...")
			return
		}
		ctx.sendData.InitData()
		if DEVICE == ctx.id {
			ctx.sendData.SendStep = DATA_DEVICE_SEND_STEP
		} else {
			ctx.sendData.SendStep = DATA_MOBILE_SEND_STEP
		}
		data := ctx.sendData.GetSendData()
		dataSend(ctx, data)
		ctx.sendData.State = DATA_STATE_SENDING
	}
}

func onStep(ctx *IClientContex) {
	if DATA_STATE_SENDING == ctx.sendData.State {
		if time.Now().Unix()-ctx.sendData.SendTime > DATA_RETRY_TIMEOUT {
			if ctx.sendData.RetryCnt > 0 {
				log.Printf("time out, retry data sending, retryCnt = %d\r\n", ctx.sendData.RetryCnt)
				data := ctx.sendData.GetLastSendData()
				dataSend(ctx, data)
				ctx.sendData.RetryCnt -= 1
			} else {
				// give up current data, init dataBlock
				ctx.sendData.Init()
			}
		}
	}

	if DATA_STATE_DONE == ctx.recieveData.State {
		log.Print("received data:\r\n")
		ctx.recieveData.dumpData(1)
		ctx.recieveData.Init()
	}

	if ctx.fileTranMode {
		if ctx.fileInfo.File == nil {
			info, err := os.Stat(ctx.fileInfo.FilePath)
			if err != nil {
				log.Printf("get file info error, file: %s", ctx.fileInfo.FilePath)
				ctx.fileTranMode = false
				os.Exit(0)
				return
			}
			ctx.fileInfo.Init()
			ctx.fileInfo.FileSize = uint32(info.Size())
			fileObj, err := os.Open(ctx.fileInfo.FilePath)
			if err != nil {
				log.Printf("open file failed, file: %s", ctx.fileInfo.FilePath)
				os.Exit(0)
			}

			ctx.fileInfo.File = fileObj
			ctx.fileInfo.TranLen = 0
			ctx.fileInfo.SendSeq = 0
			startTrans(ctx)
			ctx.fileInfo.State = DATA_STATE_TRANS_REQ
		}

		if DATA_STATE_SENDING == ctx.fileInfo.State {
			if ctx.fileInfo.SendTime > 0 && time.Now().Unix()-ctx.fileInfo.SendTime > DATA_RETRY_TIMEOUT {
				if ctx.fileInfo.RetryCnt > 0 {
					log.Printf("time out, retry data transing, retryCnt = %d\r\n", ctx.fileInfo.RetryCnt)
					data := ctx.fileInfo.GetLastFileData()
					dataUpload(ctx, data)
					ctx.fileInfo.RetryCnt -= 1
				} else {
					// give up current data, init dataBlock
					ctx.fileInfo.Init()
				}
			}
		}
	}

}

func dataUpload(ctx *IClientContex, data []byte) {
	if data == nil {
		log.Printf("data is empty\r\n")
		return
	}

	//log.Printf("data sending, seq = %d, len = %d\r\n", data[4], len(data)-6)
	var resp IMessage
	resp.MsgSrc = ctx.id
	resp.MsgType = MSG_TRANS_DATA
	resp.MsgId = ctx.MsgId
	ctx.MsgId += 1

	resp.Payload = data
	ctx.sendMessage(resp)
}

func dataSend(ctx *IClientContex, data []byte) {
	if data == nil {
		log.Printf("data is empty\r\n")
		return
	}

	//log.Printf("data sending, seq = %d, len = %d\r\n", data[4], len(data)-6)
	var resp IMessage
	resp.MsgSrc = ctx.id
	resp.MsgType = MSG_POST_DATA
	resp.MsgId = ctx.MsgId
	ctx.MsgId += 1

	resp.Payload = data
	ctx.sendMessage(resp)
}

// -------msg handle function---------------------------

func onDataReceived(ctx *IClientContex, msg IMessage) {
	var resp IMessage
	var ret = ctx.recieveData.OnReceive(msg.Payload)

	resp.MsgSrc = ctx.id
	resp.MsgId = msg.MsgId
	resp.MsgType = ret
	resp.Payload = nil
	resp.MsgDst = msg.MsgSrc

	ctx.sendMessage(resp)
}

func onDataContinue(ctx *IClientContex, msg IMessage) {
	var blockData *IDataExample = &ctx.sendData
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
	dataSend(ctx, data)
}

func onDataError(ctx *IClientContex, msg IMessage) {
	var blockData *IDataExample = &ctx.sendData
	// try send again
	if DATA_STATE_SENDING == blockData.State {
		data := blockData.GetLastSendData()
		dataSend(ctx, data)
	}
}

func onDataAckDone(ctx *IClientContex, msg IMessage) {
	var blockData *IDataExample = &ctx.sendData
	// Init block data, prepare for next receive & send
	blockData.Init()
}

func onTransContinue(ctx *IClientContex, msg IMessage) {
	if !ctx.fileTranMode {
		log.Printf("client mode error, invalid request!\r\n")
		return
	}

	var fileInfo *IFileInfo = &ctx.fileInfo
	if DATA_STATE_SENDING != fileInfo.State {
		log.Printf("invalid request!\r\n")
		return
	}

	data := fileInfo.GetFileData()

	if data == nil {
		log.Printf("data is empty, request from 0x%X\r\n", msg.MsgSrc)
		fileInfo.Init()
		ctx.fileTranMode = false
		return
	}
	dataUpload(ctx, data)
}

func onTransError(ctx *IClientContex, msg IMessage) {
	var fileInfo *IFileInfo = &ctx.fileInfo
	// try send again
	if fileInfo.State == DATA_STATE_TRANS_REQ {
		log.Printf("trans request refused by server :(\r\n")
		fileInfo.Init()
		ctx.fileTranMode = false
		return
	}

	if fileInfo.RetryCnt > 0 {
		if DATA_STATE_SENDING == fileInfo.State {
			data := fileInfo.GetLastFileData()
			dataUpload(ctx, data)
		}
		fileInfo.RetryCnt -= 1
	} else {
		fileInfo.Init()
	}
}

func onTransAckDone(ctx *IClientContex, msg IMessage) {
	var fileInfo *IFileInfo = &ctx.fileInfo
	fileInfo.Init()
	log.Printf("file upload success!\r\n")
	ctx.onExit()
	os.Exit(0)
}

func onTransAck(ctx *IClientContex, msg IMessage) {
	if !ctx.fileTranMode {
		log.Printf("error mode\r\n")
		return
	}

	ctx.fileInfo.State = DATA_STATE_SENDING
	onTransContinue(ctx, msg)
}

func startTrans(ctx *IClientContex) {
	fileName := path.Base(ctx.fileInfo.FilePath)

	var resp IMessage
	resp.MsgSrc = ctx.id
	resp.MsgType = MSG_TRANS_START
	resp.MsgId = ctx.MsgId
	ctx.MsgId += 1

	data := make([]byte, 4)
	binary.LittleEndian.PutUint32(data, ctx.fileInfo.FileSize)
	data = append(data, fileName...)
	resp.Payload = data
	ctx.sendMessage(resp)
}
