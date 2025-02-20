import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import {useEffect, useState} from "react";
import {AssistantService, ClientService} from "Frontend/generated/endpoints";
import InterViewRecords from "../generated/com/guangge/Interview/writtentest/InterViewRecord";
import {GridColumn} from "@vaadin/react-components/GridColumn";
import {Grid,type GridEventContext} from "@vaadin/react-components/Grid";
import {MessageInput} from "@vaadin/react-components/MessageInput";
import {nanoid} from "nanoid";
import {SplitLayout} from "@vaadin/react-components/SplitLayout";
import Message, {MessageItem} from "../components/Message";
import MessageList from "Frontend/components/MessageList";
import { Button, Notification, TextField } from '@vaadin/react-components';
import { Tooltip } from '@vaadin/react-components/Tooltip.js';
import axios from 'axios';

export const config: ViewConfig = { menu: { order: 2, icon: 'line-awesome/svg/globe-solid.svg' }, title: '面试结果' };

export default function InterviewView() {
  const [chatId, setChatId] = useState(nanoid());
  const [working, setWorking] = useState(false);
  const [interViews, setInterView] = useState<InterViewRecord[]>([]);

  useEffect(() => {
    // Update bookings when we have received the full response
    if (!working) {
      ClientService.getInterView().then(setInterView);
    }
  }, [working]);

const statusRenderer = (status: string) => (
      <span {...{ theme: `badge ${status === '淘汰' ? 'error' : 'success'}` }}>
         {status}
      </span>
);

const tooltipGenerator = (context: GridEventContext<interViews>): string => {
  let text = '';

  const { column, item } = context;
  if (column && item && column.path == 'evaluate') {
    text = item.evaluate;
  }

  return text;
};

const handleUpload = async (number: String, event) => {
    const file = event.target.files[0];
    if (!file) {
      alert('请先选择简历');
      return;
    }

    const formData = new FormData();
    formData.append('file', file);
    formData.append('id', number);

    try {
      const response = await axios.post('http://localhost:8080/fileupload/resume/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      alert('上传成功');
      console.log('Response:', response.data);
    } catch (error) {
      alert('上传失败');
      console.error('Error:', error);
    }
  };

  return (
    <SplitLayout className="h-full">
      <div className="flex flex-col gap-m p-m box-border" style={{width: '100%'}} >
        <h3>候选者名单</h3>
        <Grid items={interViews} className="flex-shrink-0" theme="row-stripes">
          <GridColumn path="number" header="序号" autoWidth/>
          <GridColumn path="name" header="姓名" autoWidth/>
          <GridColumn path="score" header="得分" autoWidth/>
          <GridColumn header="评价" >
          {({ item: interView }) => statusRenderer(interView.interViewStatus)}
          </GridColumn>
          <GridColumn path="email" header="邮箱" autoWidth/>
          <GridColumn path="evaluate" header="评语" >
          {({ item: interView  }) => (
                <span>
                  {interView.evaluate}
                </span>
              )}
          </GridColumn>
          <GridColumn header="发信" >
                {({ item: interView }) => (
                  <Button
                     onClick={() => {
                              BookingService.sendMail(interView.number,interView.name);
                            }}>
                     ✉
                  </Button>
                )}
          </GridColumn>
          <GridColumn header="上传简历" >
              {({ item: interView }) => (
                 <input type="file" onChange={(e) => handleUpload(interView.number, e)} />
              )}
            </GridColumn>
          <Tooltip slot="tooltip" generator={tooltipGenerator} />
        </Grid>
      </div>
    </SplitLayout>

  );
}
