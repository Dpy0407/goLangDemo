package main

import (
	. "../blockData"
	. "../message"
	"fmt"
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
	}
}

func onMsgRecieved(ctx *IClientContex, msg *IMessage) {
	switch msg.MsgType {
	case MSG_PUT_DATA:
		onDataReceived(ctx, *msg)
		break
	case MSG_DATA_CONTINUE:
		onDataContinue(ctx, *msg)
		break
	case MSG_DATA_ERROR:
		onDataError(ctx, *msg)
		break
	case MSG_DATA_ACK_DONE:
		onDataAckDone(ctx, *msg)
		break
	default:
		break
	}
}

func onCommand(ctx *IClientContex, cmd string) {
	cmd = strings.ToLower(cmd)
	if cmd == "send" {
		if DATA_STATE_SENDING == ctx.sendData.State {
			fmt.Println("busy on sending...")
			return
		}
		ctx.sendData.InitData()
		data := ctx.sendData.GetSendData()
		dataSend(ctx, data)
		ctx.sendData.State = DATA_STATE_SENDING
	}
}

func onStep(ctx *IClientContex) {
	if DATA_STATE_SENDING == ctx.sendData.State {
		if time.Now().Unix()-ctx.sendData.SendTime > DATA_RETRY_TIMEOUT {
			if ctx.sendData.RetryCnt > 0 {
				fmt.Printf("retry data sending, retryCnt = %d\r\n", ctx.sendData.RetryCnt)
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
		fmt.Print("received data:\r\n")
		ctx.recieveData.dumpData(1)
		ctx.recieveData.Init()
	}
}

func dataSend(ctx *IClientContex, data []byte) {
	if data == nil {
		fmt.Printf("data is empty\r\n")
		return
	}

	//fmt.Printf("data sending, seq = %d, len = %d\r\n", data[4], len(data)-6)
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
		fmt.Printf("invalid request!\r\n")
		return
	}

	data := blockData.GetSendData()

	if data == nil {
		fmt.Printf("data is empty, request from 0x%X\r\n", msg.MsgSrc)
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
