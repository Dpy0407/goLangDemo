package main

import (
	"fmt"
	"time"
)
import . "../message"
import . "../blockData"

func deviceProcessLoop(ctx *IContex) {
	ctx.device2mobileData.Init()
	ctx.device2mobileData.SendStep = DATA_MOBILE_SEND_STEP
	for true {
		select {
		case msg := <-main2deviceChan:
			onMsgReceived(ctx, *msg)

		default:
			deviceStep(ctx)
		}

	}
}

func deviceStep(ctx *IContex) {
	if DATA_STATE_SEND_REQUIRED == ctx.device2mobileData.State {
		data := ctx.device2mobileData.GetSendData()
		dataSend(ctx, MOBILE, data)
		ctx.device2mobileData.State = DATA_STATE_SENDING
	} else if DATA_STATE_SENDING == ctx.device2mobileData.State {
		if time.Now().Unix()-ctx.device2mobileData.SendTime > DATA_RETRY_TIMEOUT {
			if ctx.device2mobileData.RetryCnt > 0 {
				fmt.Printf("retry data sending, retryCnt = %d\r\n", ctx.device2mobileData.RetryCnt)
				data := ctx.device2mobileData.GetLastSendData()
				dataSend(ctx, MOBILE, data)
				ctx.device2mobileData.RetryCnt -= 1
			} else {
				// give up current data, init dataBlock
				ctx.device2mobileData.Init()
			}
		}
	}
}
