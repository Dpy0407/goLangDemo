package main

import (
	. "../blockData"
	. "../message"
	"fmt"
)

func onMsgReceived(ctx *IContex, msg IMessage) {
	switch msg.MsgType {
	case MSG_POST_DATA:
		onDataReceived(ctx, msg)
		break
	case MSG_DATA_CONTINUE:
		onDataContinue(ctx, msg)
		break
	case MSG_DATA_ERROR:
		onDataError(ctx, msg)
		break
	case MSG_DATA_ACK_DONE:
		onDataAckDone(ctx, msg)
		break
	default:
		break
	}
}

func onDataReceived(ctx *IContex, msg IMessage) {
	var resp IMessage
	var ret byte

	if DIVICE == msg.MsgSrc {
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
	var data []byte
	if DIVICE == msg.MsgSrc {
		data = ctx.mobile2deviceData.GetSendData()
	} else if MOBILE == msg.MsgSrc {
		data = ctx.device2mobileData.GetSendData()
	}

	if data == nil {
		fmt.Printf("data is empty, request from 0x%X\r\n", msg.MsgSrc)
		return
	}

	dataSend(ctx, msg.MsgSrc, data)
}

func onDataError(ctx *IContex, msg IMessage) {
	var blockData IBlockData
	if DIVICE == msg.MsgSrc {
		blockData = ctx.mobile2deviceData
	} else if MOBILE == msg.MsgSrc {
		blockData = ctx.device2mobileData
	}

	// try send again
	if DATA_STATE_SENDING == blockData.State {
		data := blockData.GetLastSendData()
		dataSend(ctx, msg.MsgSrc, data)
	}

}

func onDataAckDone(ctx *IContex, msg IMessage) {
	var blockData IBlockData
	if DIVICE == msg.MsgSrc {
		blockData = ctx.mobile2deviceData
	} else if MOBILE == msg.MsgSrc {
		blockData = ctx.device2mobileData
	}

	// Init block data, prepare for next receive & send
	blockData.Init()
}

func dataSend(ctx *IContex, dst byte, data []byte) {
	if data == nil {
		fmt.Printf("data is empty\r\n")
		return
	}

	if dst != DIVICE && dst != MOBILE {
		fmt.Printf("invalid dst, dst = 0x%X", dst)
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
